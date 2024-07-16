/*
 * Copyright (C) 2015-2024 Arno Onken
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.asnelt.derandom;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ProcessingViewModel extends ViewModel {
    /** Random manager for generating predictions. */
    private final RandomManager mRandomManager;
    /** Handler for starting socket input processing. */
    private final Handler mHandler;
    /** Circular buffer for storing input numbers. */
    private final HistoryBuffer mHistoryBuffer;
    /** Circular buffer for storing predicted numbers. */
    private final HistoryBuffer mHistoryPredictionBuffer;
    /** Object for synchronizing the main thread and the processing thread. */
    private final Object mSynchronizationObject;
    /** Executor service for all processing tasks. */
    private final ExecutorService mProcessingExecutor;
    /** Executor service for server task. */
    private final ExecutorService mServerExecutor;
    /** Lock for the disconnected condition. */
    private final Lock mConnectionLock;
    /** Condition that is triggered when the client socket is disconnected. */
    private final Condition mDisconnected;
    /** Number of numbers to forecast. */
    private volatile int mPredictionLength;
    /** Flag for whether the generator should be detected automatically. */
    private volatile boolean mAutoDetect;
    /** Current input file stream for reading numbers. */
    private volatile Uri mInputUri;
    /** Reader for reading input numbers. */
    private volatile BufferedReader mInputReader;
    /** Writer for writing predictions to the client socket. */
    private volatile BufferedWriter mOutputWriter;
    /** Number of process input tasks. */
    private volatile int mInputTaskLength;
    /** Flag for whether processing should continue. */
    private volatile boolean mProcessingEnabled;
    /** Server socket port. */
    private volatile int mServerPort;
    /** Server socket. */
    private volatile ServerSocket mServerSocket;
    /** Client socket. */
    private volatile Socket mClientSocket;
    /** Current number type. */
    private volatile NumberSequence.NumberType mNumberType;
    /** Future for cancelling the server task. */
    private Future<?> mServerFuture;

    /**
     * Enumeration of all possible input types.
     * */
    enum InputType {
        DIRECT_INPUT(0), FILE_INPUT(1), SOCKET_INPUT(2);

        /** Index associated with input type for use in Spinner. */
        private final int mIndex;

        /**
         * Initializes the input type.
         * @param index the index of the input type for use in a Spinner
         */
        InputType(int index) {
            mIndex = index;
        }

        /**
         * Returns the index of the input type for use in a Spinner.
         * @return the index of the input type
         */
        public int getIndex() {
            return mIndex;
        }
    }

    /**
     * Enumeration of all possible status types.
     * */
    public enum StatusType {
        DIRECT_INPUT, FILE_INPUT, SERVER_LISTENING, CLIENT_CONNECTED, CLIENT_DISCONNECTED
    }

    /**
     * Enumeration of all possible notification types.
     * */
    enum NotificationType {
        FILE_INPUT_ABORTED, SOCKET_INPUT_ABORTED, INVALID_INPUT_NUMBER
    }

    /**
     * Event class with a notification that can be consumed.
     */
    static class NotificationEvent {
        /** Type of the notification. */
        private final NotificationType mType;
        /** Flag for whether the event was consumed. */
        private boolean mHasBeenHandled;

        /**
         * Initializes the event, setting the internal flag to not consumed.
         * @param type the type of the notification event
         */
        public NotificationEvent(NotificationType type) {
            mHasBeenHandled = false;
            mType = type;
        }

        /**
         * Gets the type of the event if not already consumed, otherwise null.
         * @return the type of the event or null
         */
        public NotificationType getTypeIfNotHandled() {
            if (mHasBeenHandled) {
                return null;
            } else {
                mHasBeenHandled = true;
                return mType;
            }
        }
    }

    /** Current input method. */
    private InputType mInputType;
    /** Current input method to display. */
    private MutableLiveData<InputType> mLiveInputType;
    /** Current history numbers and history prediction numbers to display. */
    private MutableLiveData<Pair<NumberSequence,NumberSequence>> mLiveHistoryNumbers;
    /** Current prediction numbers to display. */
    private MutableLiveData<NumberSequence> mLivePredictionNumbers;
    /** Current generator selection to display. */
    private MutableLiveData<Integer> mLiveGenerator;
    /** Current status to display. */
    private MutableLiveData<Pair<StatusType,Integer>> mLiveStatus;
    /** Current processing indicator. */
    private MutableLiveData<Boolean> mLiveIsProcessing;
    /** Current notification event. */
    private MutableLiveData<NotificationEvent> mLiveNotification;

    /**
     * Constructor for initializing the ProcessingViewModel. Generates a HistoryBuffer, a
     * HistoryPredictionBuffer, a RandomManager and an ExecutorService.
     */
    public ProcessingViewModel() {
        super();
        mPredictionLength = 0;
        mAutoDetect = false;
        mInputUri = null;
        mInputReader = null;
        mOutputWriter = null;
        mInputType = InputType.DIRECT_INPUT;
        mNumberType = NumberSequence.NumberType.RAW;
        mInputTaskLength = 0;
        mProcessingEnabled = true;
        mServerPort = 0;
        mClientSocket = null;
        mSynchronizationObject = this;
        mHistoryBuffer = new HistoryBuffer(0);
        mHistoryPredictionBuffer = new HistoryBuffer(0);
        mRandomManager = new RandomManager();
        // Handler for starting socket input processing
        mHandler = new Handler(Looper.getMainLooper());
        mProcessingExecutor = Executors.newSingleThreadExecutor();
        mServerExecutor = Executors.newSingleThreadExecutor();
        mConnectionLock = new ReentrantLock();
        mDisconnected = mConnectionLock.newCondition();
        mServerFuture = null;
    }

    /**
     * Called when the view is cleared.
     */
    @Override
    public void onCleared() {
        mProcessingEnabled = false;
        // Shutdown server thread
        mServerExecutor.shutdownNow();
        // Shutdown processing thread
        mProcessingExecutor.shutdownNow();
        // Close all sockets
        closeSockets();
        super.onCleared();
    }

    /**
     * Returns the object indicating the current input type to be displayed.
     * @return the object indicating the current input type
     */
    LiveData<InputType> getLiveInputType() {
        if (mLiveInputType == null) {
            mLiveInputType = new MutableLiveData<>();
        }
        return mLiveInputType;
    }

    /**
     * Returns the object holding the current history numbers and history prediction numbers to be
     * displayed.
     * @return the object holding the current history and history prediction numbers
     */
    LiveData<Pair<NumberSequence,NumberSequence>> getLiveHistoryNumbers() {
        if (mLiveHistoryNumbers == null) {
            mLiveHistoryNumbers = new MutableLiveData<>();
        }
        return mLiveHistoryNumbers;
    }

    /**
     * Returns the object holding the current prediction numbers to be displayed.
     * @return the object holding the current prediction numbers
     */
    LiveData<NumberSequence> getLivePredictionNumbers() {
        if (mLivePredictionNumbers == null) {
            mLivePredictionNumbers = new MutableLiveData<>();
        }
        return mLivePredictionNumbers;
    }

    /**
     * Returns the object holding the current generator selection to be displayed.
     * @return the object holding the current generator selection
     */
    LiveData<Integer> getLiveGenerator() {
        if (mLiveGenerator == null) {
            mLiveGenerator = new MutableLiveData<>();
        }
        return mLiveGenerator;
    }

    /**
     * Returns the object holding the current status to be displayed.
     * @return the object holding the current status
     */
    LiveData<Pair<StatusType,Integer>> getLiveStatus() {
        if (mLiveStatus == null) {
            mLiveStatus = new MutableLiveData<>();
        }
        return mLiveStatus;
    }

    /**
     * Returns the object indicating whether processing is ongoing.
     * @return the object indicating ongoing processing
     */
    LiveData<Boolean> getLiveIsProcessing() {
        if (mLiveIsProcessing == null) {
            mLiveIsProcessing = new MutableLiveData<>();
        }
        return mLiveIsProcessing;
    }

    /**
     * Returns the object for signalling notifications to the main activity.
     * @return the object for signalling notifications
     */
    LiveData<NotificationEvent> getLiveNotification() {
        if (mLiveNotification == null) {
            mLiveNotification = new MutableLiveData<>();
        }
        return mLiveNotification;
    }

    /**
     * Executes a clear task.
     */
    void clear() {
        mRandomManager.deactivateAll();
        mProcessingEnabled = false;
        mNumberType = NumberSequence.NumberType.RAW;
        mProcessingExecutor.execute(new ProcessingViewModel.ClearTask());
    }

    /**
     * Sets the currently active generator.
     * @param generatorIndex index of the desired generator
     */
    void setCurrentGenerator(int generatorIndex) {
        if (generatorIndex != mRandomManager.getCurrentGeneratorIndex()) {
            prepareInputProcessing();
            mProcessingExecutor.execute(new ProcessingViewModel.UpdateAllTask(generatorIndex));
        }
    }

    /**
     * Returns human readable names of all generators.
     * @return all generator names
     */
    String[] getGeneratorNames() {
        return mRandomManager.getGeneratorNames();
    }

    /**
     * Returns the name of the currently active generator.
     * @return name of the currently active generator
     */
    String getCurrentGeneratorName() {
        return mRandomManager.getCurrentGeneratorName();
    }

    /**
     * Returns the parameter names of the currently active generator.
     * @return all parameter names of the currently active generator
     */
    String[] getCurrentParameterNames() {
        return mRandomManager.getCurrentParameterNames();
    }

    /**
     * Returns all parameter values of the currently active generator.
     * @return parameter values of the currently active generator
     */
    long[] getCurrentParameters() {
        return mRandomManager.getCurrentParameters();
    }

    /**
     * Sets the current input type.
     * @param inputType the new input type
     */
    void setInputType(InputType inputType) {
        if (inputType == mInputType) {
            return;
        }
        mInputType = inputType;
        if (mLiveInputType != null) {
            mLiveInputType.postValue(inputType);
        }
        if (mInputType == InputType.DIRECT_INPUT) {
            postStatus(StatusType.DIRECT_INPUT);
        }
    }

    /**
     * Returns the current input type.
     * @return the current input type
     */
    InputType getInputType() {
        return mInputType;
    }

    /**
     * Returns the current input URI or null if no input URI is set.
     * @return the current input URI
     */
    Uri getInputUri() {
        return mInputUri;
    }

    /**
     * Sets the input URI to null.
     */
    void resetInputUri() {
        mInputUri = null;
    }

    /**
     * Sets the number of numbers to predict.
     * @param predictionLength the number of numbers to predict
     */
    void setPredictionLength(int predictionLength) {
        if (mPredictionLength != predictionLength) {
            mPredictionLength = predictionLength;
            updatePrediction();
        }
    }

    /**
     * Sets the flag that determines whether the generator is detected automatically.
     * @param autoDetect automatically detect generator if true
     */
    void setAutoDetect(boolean autoDetect) {
        mAutoDetect = autoDetect;
    }

    /**
     * Sets the server port. If a server task is running, then it is restarted.
     * @param serverPort the new server port
     */
    void setServerPort(int serverPort) {
        if (mServerPort != serverPort) {
            mServerPort = serverPort;
            if (mServerFuture != null) {
                stopServerTask();
                startServerTask();
            }
        }
    }

    /**
     * Determines whether input is currently processed.
     * @return true if input is currently processed
     */
    boolean isProcessingInput() {
        return mInputTaskLength > 0;
    }

    /**
     * Executes a change capacity task.
     * @param capacity the new input history capacity
     */
    void setCapacity(int capacity) {
        mProcessingExecutor.execute(new ProcessingViewModel.ChangeCapacityTask(capacity));
    }

    /**
     * Processes an input string of newline separated integers and calculates a prediction.
     * @param input the input string to be processed
     */
    void processInputString(String input) {
        // Process input in separate thread
        prepareInputProcessing();
        mProcessingExecutor.execute(new ProcessingViewModel.ProcessInputTask(input));
    }

    /**
     * Opens and processes the input file pointed to by fileUri. Disables direct input.
     * @param fileUri the URI of the file to be processed
     * @param fileStream the file stream of the input file to be processed
     */
    void processInputFile(Uri fileUri, InputStream fileStream) {
        // Process input in separate thread
        prepareInputProcessing();
        mProcessingExecutor.execute(new ProcessingViewModel.ProcessInputTask(fileUri, fileStream));
    }

    /**
     * Starts the server task and sets the server future.
     */
    void startServerTask() {
        mServerFuture = mServerExecutor.submit(new ProcessingViewModel.ServerTask());
    }

    /**
     * Stops the server task and sets the server future to null. Closes all sockets.
     */
    void stopServerTask() {
        if (mServerFuture != null) {
            mServerFuture.cancel(true);
            mServerFuture = null;
        }
        closeSockets();
    }

    /**
     * Posts the current status to the user interface thread.
     * @param status the new status
     */
    private void postStatus(final StatusType status) {
        if (mLiveStatus != null) {
            mLiveStatus.postValue(Pair.create(status, mServerPort));
        }
    }

    /**
     * Executes a process input task with an input reader.
     */
    private void processInputSocket() {
        // Process input in separate thread
        prepareInputProcessing();
        mProcessingExecutor.execute(new ProcessingViewModel.ProcessInputTask());
    }

    /**
     * Closes the client socket and the reader and writer.
     */
    private void closeClient() {
        synchronized (mSynchronizationObject) {
            if (mInputReader != null) {
                try {
                    mInputReader.close();
                } catch (IOException e) {
                    // We do not need to do anything more with the reader
                }
            }
            if (mOutputWriter != null) {
                try {
                    mOutputWriter.close();
                } catch (IOException e) {
                    // We do not need to do anything more with the writer
                }
            }
            if (mClientSocket != null) {
                try {
                    mClientSocket.close();
                } catch (IOException e) {
                    // We do not need to do anything more with the client socket
                }
                mClientSocket = null;
            }
        }
    }

    /**
     * Closes all sockets.
     */
    private void closeSockets() {
        synchronized (mSynchronizationObject) {
            closeClient();
            if (mServerSocket != null) {
                try {
                    mServerSocket.close();
                } catch (IOException e) {
                    // We do not need to do anything more with the server socket
                }
                mServerSocket = null;
            }
        }
    }

    /**
     * Prepares input processing by incrementing the counter of active input tasks and updating the
     * progressing status.
     */
    private void prepareInputProcessing() {
        synchronized (mSynchronizationObject) {
            mInputTaskLength++;
        }
        if (mLiveIsProcessing != null) {
            mLiveIsProcessing.postValue(isProcessingInput());
        }
    }

    /**
     * Finishes input processing by decrementing the counter of active input tasks.
     */
    private void finishInputProcessing() {
        synchronized (mSynchronizationObject) {
            if (mInputTaskLength > 0) {
                mInputTaskLength--;
            }
        }
        if (mLiveIsProcessing != null) {
            mLiveIsProcessing.postValue(isProcessingInput());
        }
    }

    /**
     * Calculates a new prediction and notifies the main activity.
     */
    private void updatePrediction() {
        mProcessingExecutor.execute(new ProcessingViewModel.UpdatePredictionTask());
    }

    /**
     * This class implements a task for clearing all input.
     */
    private class ClearTask implements Runnable {
        /**
         * Starts executing the code of the task.
         */
        @Override
        public void run() {
            mHistoryBuffer.clear();
            mHistoryPredictionBuffer.clear();
            int currentGeneratorIndex = mRandomManager.getCurrentGeneratorIndex();
            mRandomManager.reset();
            mRandomManager.setCurrentGeneratorIndex(currentGeneratorIndex);
            mProcessingEnabled = true;
            if (mLiveHistoryNumbers != null) {
                mLiveHistoryNumbers.postValue(Pair.create(null, null));
            }
            if (mLivePredictionNumbers != null) {
                mLivePredictionNumbers.postValue(null);
            }
        }
    }

    /**
     * This class implements a task for changing the input history capacity.
     */
    private class ChangeCapacityTask implements Runnable {
        /** The new input capacity. */
        private final int mCapacity;

        /**
         * Constructor for setting the new input capacity.
         */
        ChangeCapacityTask(final int capacity) {
            mCapacity = capacity;
        }

        /**
         * Starts executing the code of the task.
         */
        @Override
        public void run() {
            mHistoryBuffer.setCapacity(mCapacity);
            mHistoryPredictionBuffer.setCapacity(mCapacity);
        }
    }

    /**
     * This class implements a task for updating all history predictions and predictions.
     */
    private class UpdateAllTask implements Runnable {
        /** The index of the new generator. */
        private final int mGeneratorIndex;

        /**
         * Constructor that initializes a task that changes the generator.
         * @param generatorIndex index of the new generator
         */
        UpdateAllTask(final int generatorIndex) {
            mGeneratorIndex = generatorIndex;
        }

        /**
         * Starts executing the code of the task.
         */
        @Override
        public void run() {
            final boolean generatorChanged;
            if (mRandomManager.getCurrentGeneratorIndex() != mGeneratorIndex) {
                // Process complete history
                mRandomManager.setCurrentGeneratorIndex(mGeneratorIndex);
                generatorChanged = true;
            } else {
                generatorChanged = false;
            }
            final NumberSequence historyNumbers;
            final NumberSequence historyPredictionNumbers;
            final NumberSequence predictionNumbers;
            if (generatorChanged && mHistoryBuffer.length() > 0) {
                mRandomManager.resetCurrentGenerator();
                historyNumbers = new NumberSequence(mHistoryBuffer.toArray(), mNumberType);
                mRandomManager.findCurrentSequence(historyNumbers, null);
                historyPredictionNumbers = mRandomManager.getIncomingPredictionNumbers();
                // Generate new prediction without updating the state
                predictionNumbers = mRandomManager.predict(mPredictionLength, mNumberType);
            } else {
                historyNumbers = null;
                historyPredictionNumbers = null;
                predictionNumbers = null;
            }
            finishInputProcessing();
            if (generatorChanged) {
                mHistoryPredictionBuffer.clear();
                if (historyPredictionNumbers != null){
                    mHistoryPredictionBuffer.put(historyPredictionNumbers.getInternalNumbers());
                }
                if (mLiveHistoryNumbers != null) {
                    mLiveHistoryNumbers.postValue(Pair.create(historyNumbers,
                            historyPredictionNumbers));
                }
                if (mLivePredictionNumbers != null) {
                    mLivePredictionNumbers.postValue(predictionNumbers);
                }
            }
        }
    }

    /**
     * This class implements a task that processes input numbers.
     */
    private class ProcessInputTask implements Runnable {
        /** Input string of newline separated integers. */
        private final String mInput;
        /** File input URI. */
        private final Uri mFileUri;
        /** File input stream. */
        private final InputStream mFileStream;

        /**
         * Constructor for processing an input string.
         * @param input the input string to be processed
         */
        ProcessInputTask(final String input) {
            mInput = input;
            mFileUri = null;
            mFileStream = null;
        }

        /**
         * Constructor for processing the input file pointed to by fileUri.
         * @param fileUri the URI of the file to be processed
         * @param fileStream the file stream of the file to be processed
         */
        ProcessInputTask(final Uri fileUri, final InputStream fileStream) {
            mInput = null;
            mFileUri = fileUri;
            mFileStream = fileStream;
        }

        /**
         * Constructor for processing input from current input reader.
         */
        ProcessInputTask() {
            mInput = null;
            mFileUri = null;
            mFileStream = null;
        }

        /**
         * Starts executing the code of the task.
         */
        @Override
        public void run() {
            try {
                if (mInput != null) {
                    processInputString(mInput);
                } else if (mFileUri != null) {
                    processFileInput();
                } else if (mInputReader != null) {
                    processSocketInput();
                }
            } catch (NumberFormatException e) {
                // Show error message in main activity
                if (mLiveNotification != null) {
                    mLiveNotification.postValue(
                            new NotificationEvent(NotificationType.INVALID_INPUT_NUMBER));
                }
            }
            finishInputProcessing();
        }

        /**
         * Opens the input reader from the mInputUri and initializes processing of the reader.
         */
        private void processFileInput() {
            mInputUri = mFileUri;
            if (mFileStream == null) {
                abortFileInput();
                return;
            }
            postStatus(StatusType.FILE_INPUT);
            mInputReader = new BufferedReader(new InputStreamReader(mFileStream));
            try {
                while (mInputReader.ready() && mProcessingEnabled) {
                    String nextInput = mInputReader.readLine();
                    if (nextInput == null) {
                        break;
                    }
                    processInputString(nextInput);
                }
                mInputReader.close();
            } catch (IOException | NullPointerException e) {
                abortFileInput();
            }
            try {
                mInputReader.close();
            } catch (IOException e) {
                // We do not need to do anything more with this mInputReader
            }
            mInputReader = null;
        }

        /**
         * Reads input from the input reader and assembles an input string to be processed.
         */
        private void processSocketInput() {
            mConnectionLock.lock();
            try {
                while (mClientSocket != null && !mClientSocket.isClosed()) {
                    String nextInput = mInputReader.readLine();
                    if (nextInput == null) {
                        break;
                    }
                    try {
                        processInputString(nextInput);
                    } catch (NumberFormatException e) {
                        // Show error message in main activity
                        if (mLiveNotification != null) {
                            mLiveNotification.postValue(
                                    new NotificationEvent(NotificationType.INVALID_INPUT_NUMBER));
                        }
                    }
                }
                mDisconnected.signal();
            } catch (IOException | NullPointerException e) {
                // Ignore exception
            } finally {
                mConnectionLock.unlock();
            }
        }

        /**
         * Aborts file input processing and updates the user interface.
         */
        private void abortFileInput() {
            if (mInputReader != null) {
                try {
                    mInputReader.close();
                } catch (IOException e) {
                    // We do not need to do anything more with this mInputReader
                }
                mInputReader = null;
            }
            if (mInputUri != null) {
                mInputUri = null;
            }
            setInputType(InputType.DIRECT_INPUT);
            if (mLiveNotification != null) {
                mLiveNotification.postValue(
                        new NotificationEvent(NotificationType.FILE_INPUT_ABORTED));
            }
        }

        /**
         * Processes the given input string by parsing the input string and processing the numbers.
         * @param inputString string of newline separated integers
         */
        private void processInputString(String inputString) {
            NumberSequence inputNumbers;
            String[] stringNumbers = inputString.split("\n");
            inputNumbers = new NumberSequence(stringNumbers, mNumberType);
            NumberSequence.NumberType inputNumberType = inputNumbers.getNumberType();
            if (inputNumberType != mNumberType) {
                // Reformat history numbers
                NumberSequence historyNumbers = new NumberSequence(mHistoryBuffer.toArray(),
                        mNumberType);
                historyNumbers.formatNumbers(inputNumberType);
                mHistoryBuffer.clear();
                mHistoryPredictionBuffer.clear();
                int currentGeneratorIndex = mRandomManager.getCurrentGeneratorIndex();
                mRandomManager.reset();
                mRandomManager.setCurrentGeneratorIndex(currentGeneratorIndex);
                mNumberType = inputNumberType;
                inputNumbers = historyNumbers.concatenate(inputNumbers);
            }
            processInputNumbers(inputNumbers);
        }

        /**
         * Processes the given input numbers searching for compatible generator states. The
         * generator is eventually changed if the flag mAutoDetect is set and a better generator is
         * detected.
         * @param inputNumbers the number sequence to be processed
         */
        private void processInputNumbers(NumberSequence inputNumbers) {
            if (mAutoDetect) {
                // Detect best generator and update all states
                int bestGenerator = mRandomManager.detectGenerator(inputNumbers, mHistoryBuffer);
                if (bestGenerator != mRandomManager.getCurrentGeneratorIndex()) {
                    // Set generator and process complete history
                    mRandomManager.setCurrentGeneratorIndex(bestGenerator);
                    mRandomManager.resetCurrentGenerator();
                    NumberSequence historyNumbers = new NumberSequence(mHistoryBuffer.toArray(),
                            mNumberType);
                    mRandomManager.findCurrentSequence(historyNumbers, null);
                    NumberSequence replacedNumbers = mRandomManager.getIncomingPredictionNumbers();
                    mRandomManager.findCurrentSequence(inputNumbers, mHistoryBuffer);
                    mHistoryPredictionBuffer.clear();
                    if (replacedNumbers != null){
                        mHistoryPredictionBuffer.put(replacedNumbers.getInternalNumbers());
                    }
                    // Post change to user interface
                    showGeneratorChange(bestGenerator);
                }
            } else {
                mRandomManager.findCurrentSequence(inputNumbers, mHistoryBuffer);
            }
            NumberSequence inputHistoryPredictionNumbers =
                    mRandomManager.getIncomingPredictionNumbers();
            // Generate new prediction without updating the state
            NumberSequence predictionNumbers = mRandomManager.predict(mPredictionLength,
                    mNumberType);
            mHistoryBuffer.put(inputNumbers.getInternalNumbers());
            if (inputHistoryPredictionNumbers != null) {
                mHistoryPredictionBuffer.put(inputHistoryPredictionNumbers.getInternalNumbers());
            }
            NumberSequence historyNumbers = new NumberSequence(mHistoryBuffer.toArray(),
                    mNumberType);
            NumberSequence historyPredictionNumbers = new NumberSequence(
                    mHistoryPredictionBuffer.toArray(), mNumberType);

            // Post result to user interface
            showInputUpdate(historyNumbers, historyPredictionNumbers, predictionNumbers);
        }

        /**
         * Sends the generator change to the main activity.
         * @param bestGenerator index of the best generator
         */
        private void showGeneratorChange(final int bestGenerator) {
            if (mLiveGenerator != null) {
                mLiveGenerator.postValue(bestGenerator);
            }
        }

        /**
         * Sends the processing result to the main activity.
         * @param historyNumbers the complete sequence of history numbers
         * @param historyPredictionNumbers the complete sequence of history prediction numbers
         * @param predictionNumbers the predicted numbers
         */
        private void showInputUpdate(final NumberSequence historyNumbers,
                                     final NumberSequence historyPredictionNumbers,
                                     final NumberSequence predictionNumbers) {
            // Update live objects
            if (mLiveHistoryNumbers != null) {
                mLiveHistoryNumbers.postValue(Pair.create(historyNumbers,
                        historyPredictionNumbers));
            }
            if (mLivePredictionNumbers != null) {
                mLivePredictionNumbers.postValue(predictionNumbers);
            }
            writeSocketOutput(predictionNumbers);
        }

        /**
         * Writes the prediction numbers to the client socket. Writes an additional newline after
         * the prediction block.
         * @param predictionNumbers the predicted numbers
         */
        private void writeSocketOutput(NumberSequence predictionNumbers) {
            if (mOutputWriter != null && predictionNumbers != null) {
                try {
                    // Write numbers to output stream
                    for (int i = 0; i < predictionNumbers.length(); i++) {
                        mOutputWriter.write(predictionNumbers.toString(i));
                        mOutputWriter.newLine();
                    }
                    // Finish this sequence of numbers with an additional newline
                    mOutputWriter.newLine();
                    mOutputWriter.flush();
                } catch (IOException | NullPointerException e) {
                    // Ignore unsuccessful writes
                }
            }
        }
    }

    /**
     * This class implements a task for updating the generator prediction.
     */
    private class UpdatePredictionTask implements Runnable {
        /**
         * Starts executing the code of the task.
         */
        @Override
        public void run() {
            // Generate new prediction without updating the state
            final NumberSequence predictionNumbers;
            if (mHistoryBuffer.length() > 0) {
                predictionNumbers = mRandomManager.predict(mPredictionLength, mNumberType);
            } else {
                predictionNumbers = null;
            }
            if (mLivePredictionNumbers != null) {
                mLivePredictionNumbers.postValue(predictionNumbers);
            }
        }
    }

    /**
     * This class implements a task for establishing a socket connection.
     */
    private class ServerTask implements Runnable {
        /**
         * Starts executing the code of the task.
         */
        @Override
        public void run() {
            mConnectionLock.lock();
            try {
                try {
                    mServerSocket = new ServerSocket(mServerPort);
                } catch (IOException e) {
                    abortSocketInput();
                    return;
                }
                while (!Thread.currentThread().isInterrupted()) {
                    if (mClientSocket == null || mClientSocket.isClosed()) {
                        try {
                            // Display information about the server socket
                            postStatus(StatusType.SERVER_LISTENING);
                            mClientSocket = mServerSocket.accept();
                            postStatus(StatusType.CLIENT_CONNECTED);
                            InputStream inputStream = mClientSocket.getInputStream();
                            mInputReader = new BufferedReader(new InputStreamReader(inputStream));
                            OutputStream outputStream = mClientSocket.getOutputStream();
                            mOutputWriter = new BufferedWriter(new OutputStreamWriter(
                                    outputStream));
                        } catch (IOException e) {
                            closeClient();
                            continue;
                        }
                        boolean posted = mHandler.post(
                                ProcessingViewModel.this::processInputSocket);
                        if (!posted) {
                            abortSocketInput();
                            return;
                        }
                    }
                    try {
                        mDisconnected.await();
                        closeClient();
                        postStatus(StatusType.CLIENT_DISCONNECTED);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } finally {
                mConnectionLock.unlock();
                closeSockets();
            }
        }

        /**
         * Aborts socket input processing and updates the user interface.
         */
        private void abortSocketInput() {
            closeSockets();
            setInputType(InputType.DIRECT_INPUT);
            if (mLiveNotification != null) {
                mLiveNotification.postValue(
                        new NotificationEvent(NotificationType.SOCKET_INPUT_ABORTED));
            }
        }
    }
}