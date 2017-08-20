/*
 * Copyright (C) 2015-2017 Arno Onken
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

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
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

/**
 * This class implements a fragment for doing generator related processing. The fragment is retained
 * across configuration changes.
 */
public class ProcessingFragment extends Fragment {
    /**
     * Interface for listening to processing changes.
     */
    interface ProcessingFragmentListener {
        /**
         * Called when the history prediction was completely replaced.
         * @param historyNumbers previously entered numbers
         * @param historyPredictionNumbers predictions for previous numbers
         */
        void onHistoryPredictionReplaced(NumberSequence historyNumbers,
                                         NumberSequence historyPredictionNumbers);

        /**
         * Called when the random number generator selection changed.
         * @param generatorIndex index of new generator
         */
        void onGeneratorChanged(int generatorIndex);

        /**
         * Called when the input history changed.
         * @param inputNumbers the entered numbers
         * @param predictionNumbers predictions for entered numbers
         */
        void onHistoryChanged(NumberSequence inputNumbers, NumberSequence predictionNumbers);

        /**
         * Called when the predictions for upcoming numbers changed.
         * @param predictionNumbers predictions of upcoming numbers
         */
        void onPredictionChanged(NumberSequence predictionNumbers);

        /**
         * Called when setting the input method to an input file is aborted and sets the input
         * method back to direct input.
         */
        void onFileInputAborted();

        /**
         * Called when setting the input method to an input socket is aborted and sets the input
         * method back to direct input.
         */
        void onSocketInputAborted();

        /**
         * Called when invalid numbers where entered.
         */
        void onInvalidInputNumber();

        /**
         * Called when the input was cleared.
         */
        void onClear();

        /**
         * Called when the progress status changed.
         */
        void onProgressUpdate();

        /**
         * Called when the status of the network socket changed.
         * @param newStatus a description of the new status
         */
        void onSocketStatusChanged(String newStatus);
    }

    /** Random manager for generating predictions. */
    private final RandomManager mRandomManager;
    /** Handler for updating the user interface. */
    private final Handler mHandler;
    /** Circular buffer for storing input numbers. */
    private final HistoryBuffer mHistoryBuffer;
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
    /** Current input file for reading numbers. */
    private volatile Uri mInputUri;
    /** Reader for reading input numbers. */
    private volatile BufferedReader mInputReader;
    /** Writer for writing predictions to the client socket. */
    private volatile BufferedWriter mOutputWriter;
    /** Flag for whether a user interface update was missed during a configuration change. */
    private volatile boolean mMissingUpdate;
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
    /** Listener for processing changes. */
    private ProcessingFragmentListener mListener;
    /** Future for cancelling the server task. */
    private Future<?> mServerFuture;
    /** Index of selected input method. */
    private int mInputSelection;

    /**
     * Constructor for initializing the processing fragment. Generates a HistoryBuffer, a
     * RandomManager and an ExecutorService.
     */
    public ProcessingFragment() {
        super();
        mPredictionLength = 0;
        mAutoDetect = false;
        mInputUri = null;
        mInputReader = null;
        mOutputWriter = null;
        mMissingUpdate = false;
        mInputSelection = 0;
        mNumberType = NumberSequence.NumberType.RAW;
        mInputTaskLength = 0;
        mProcessingEnabled = true;
        mServerPort = 0;
        mClientSocket = null;
        mSynchronizationObject = this;
        mHistoryBuffer = new HistoryBuffer(0);
        mRandomManager = new RandomManager();
        // Handler for processing user interface updates
        mHandler = new Handler(Looper.getMainLooper());
        mProcessingExecutor = Executors.newSingleThreadExecutor();
        mServerExecutor = Executors.newSingleThreadExecutor();
        mConnectionLock = new ReentrantLock();
        mDisconnected = mConnectionLock.newCondition();
        mServerFuture = null;
    }

    /**
     * Initializes this activity. Called only once since the fragment is retained across
     * configuration changes.
     * @param savedInstanceState Bundle with saved state
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Retain fragment across configuration changes
        setRetainInstance(true);
    }

    /**
     * Called when the fragment is associated with an activity.
     * @param context the context the fragment is associated with
     */
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            mListener = (ProcessingFragmentListener) context;
        }
    }

    /**
     * Called before the fragment is no longer associated with an activity.
     */
    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * Sets the currently active generator.
     * @param index index of the currently active generator
     */
    public void setCurrentGenerator(int index) {
        if (index != mRandomManager.getCurrentGeneratorIndex()) {
            prepareInputProcessing();
            mProcessingExecutor.execute(new UpdateAllTask(index));
        }
    }

    /**
     * Returns human readable names of all generators.
     * @return all generator names
     */
    public String[] getGeneratorNames() {
        return mRandomManager.getGeneratorNames();
    }

    /**
     * Returns the name of the currently active generator.
     * @return name of the currently active generator
     */
    public String getCurrentGeneratorName() {
        return mRandomManager.getCurrentGeneratorName();
    }

    /**
     * Returns the parameter names of the currently active generator.
     * @return all parameter names of the currently active generator
     */
    public String[] getCurrentParameterNames() {
        return mRandomManager.getCurrentParameterNames();
    }

    /**
     * Returns all parameter values of the currently active generator.
     * @return parameter values of the currently active generator
     */
    public long[] getCurrentParameters() {
        return mRandomManager.getCurrentParameters();
    }

    /**
     * Sets the current input selection index.
     * @param inputSelection the new input selection index
     */
    public void setInputSelection(int inputSelection) {
        mInputSelection = inputSelection;
    }

    /**
     * Returns the current input selection index.
     * @return the current input selection index
     */
    public int getInputSelection() {
        return mInputSelection;
    }

    /**
     * Returns the current input URI or null if no input URI is set.
     * @return the current input URI
     */
    public Uri getInputUri() {
        return mInputUri;
    }

    /**
     * Sets the input URI to null.
     */
    public void resetInputUri() {
        mInputUri = null;
    }

    /**
     * Sets the number of numbers to predict.
     * @param predictionLength the number of numbers to predict
     */
    public void setPredictionLength(int predictionLength) {
        if (mPredictionLength != predictionLength) {
            mPredictionLength = predictionLength;
            updatePrediction();
        }
    }

    /**
     * Sets the flag that determines whether the generator is detected automatically..
     * @param autoDetect automatically detect generator if true
     */
    public void setAutoDetect(boolean autoDetect) {
        mAutoDetect = autoDetect;
    }

    /**
     * Sets the server port. If a server task is running, then it is restarted.
     * @param serverPort the new server port
     */
    public void setServerPort(int serverPort) {
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
    public boolean isProcessingInput() {
        return mInputTaskLength > 0;
    }

    /**
     * Determines whether a user interface update was missed during a configuration change.
     * @return true if an update was missed
     */
    public boolean isMissingUpdate() {
        return mMissingUpdate;
    }

    /**
     * Executes a clear task.
     */
    public void clear() {
        mRandomManager.deactivateAll();
        mProcessingEnabled = false;
        mNumberType = NumberSequence.NumberType.RAW;
        mProcessingExecutor.execute(new ClearTask());
    }

    /**
     * Executes a change capacity task.
     * @param capacity the new input history capacity
     */
    public void setCapacity(int capacity) {
        mProcessingExecutor.execute(new ChangeCapacityTask(capacity));
    }

    /**
     * Executes an update task.
     */
    public void updateAll() {
        prepareInputProcessing();
        mProcessingExecutor.execute(new UpdateAllTask());
    }

    /**
     * Processes an input string of newline separated integers and calculates a prediction.
     * @param input the input string to be processed
     */
    public void processInputString(String input) {
        // Process input in separate thread
        prepareInputProcessing();
        mProcessingExecutor.execute(new ProcessInputTask(input));
    }

    /**
     * Executes a process input task with an input reader.
     */
    public void processInputSocket() {
        // Process input in separate thread
        prepareInputProcessing();
        mProcessingExecutor.execute(new ProcessInputTask());
    }

    /**
     * Opens and processes the input file pointed to by fileUri. Disables direct input.
     * @param fileUri the URI of the file to be processed
     */
    public void processInputFile(Uri fileUri) {
        // Process input in separate thread
        prepareInputProcessing();
        mProcessingExecutor.execute(new ProcessInputTask(fileUri));
    }

    /**
     * Starts the server task and sets the server future.
     */
    public void startServerTask() {
        mServerFuture = mServerExecutor.submit(new ServerTask());
    }

    /**
     * Stops the server task and sets the server future to null. Closes all sockets.
     */
    public void stopServerTask() {
        if (mServerFuture != null) {
            mServerFuture.cancel(true);
            mServerFuture = null;
        }
        closeSockets();
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
     * Called when the activity is destroyed.
     */
    @Override
    public void onDestroy() {
        mProcessingEnabled = false;
        // Shutdown server thread
        mServerExecutor.shutdownNow();
        // Shutdown processing thread
        mProcessingExecutor.shutdownNow();
        // Close all sockets
        closeSockets();
        super.onDestroy();
    }

    /**
     * Prepares input processing by incrementing the counter of active input tasks and updating the
     * progressing status.
     */
    private void prepareInputProcessing() {
        synchronized (mSynchronizationObject) {
            mInputTaskLength++;
        }
        if (mListener != null) {
            mListener.onProgressUpdate();
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
    }

    /**
     * Calculates a new prediction and notifies the mListener.
     */
    private void updatePrediction() {
        mProcessingExecutor.execute(new UpdatePredictionTask());
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
            int currentGeneratorIndex = mRandomManager.getCurrentGeneratorIndex();
            mRandomManager.reset();
            mRandomManager.setCurrentGeneratorIndex(currentGeneratorIndex);
            boolean posted = mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onClear();
                        mMissingUpdate = false;
                    } else {
                        mMissingUpdate = true;
                    }
                }
            });
            if (!posted) {
                mMissingUpdate = true;
            }
            mProcessingEnabled = true;
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
        }
    }

    /**
     * This class implements a task for updating all history predictions and predictions.
     */
    private class UpdateAllTask implements Runnable {
        /** The index of the new generator. */
        private final int mGeneratorIndex;
        /** Flag that determines whether the generator should be changed. */
        private final boolean mChangeGenerator;

        /**
         * Standard constructor that initializes a task that does not change the generator.
         */
        UpdateAllTask() {
            mGeneratorIndex = 0;
            mChangeGenerator = false;
        }

        /**
         * Constructor that initializes a task that changes the generator.
         * @param generatorIndex index of the new generator
         */
        UpdateAllTask(final int generatorIndex) {
            mGeneratorIndex = generatorIndex;
            mChangeGenerator = true;
        }

        /**
         * Starts executing the code of the task.
         */
        @Override
        public void run() {
            final boolean generatorChanged;
            if (mChangeGenerator && mRandomManager.getCurrentGeneratorIndex() != mGeneratorIndex) {
                // Process complete history
                mRandomManager.setCurrentGeneratorIndex(mGeneratorIndex);
                generatorChanged = true;
            } else {
                generatorChanged = false;
            }
            final NumberSequence historyNumbers;
            final NumberSequence historyPredictionNumbers;
            final NumberSequence predictionNumbers;
            if ((generatorChanged || !mChangeGenerator) && mHistoryBuffer.length() > 0) {
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
            boolean posted = mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (generatorChanged || !mChangeGenerator) {
                        if (mListener != null) {
                            mListener.onProgressUpdate();
                            mListener.onHistoryPredictionReplaced(historyNumbers,
                                    historyPredictionNumbers);
                            mListener.onPredictionChanged(predictionNumbers);
                            mMissingUpdate = false;
                        } else {
                            mMissingUpdate = true;
                        }
                    }
                }
            });
            if (!posted) {
                mMissingUpdate = true;
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

        /**
         * Constructor for processing an input string.
         * @param input the input string to be processed
         */
        ProcessInputTask(final String input) {
            mInput = input;
            mFileUri = null;
        }

        /**
         * Constructor for processing the input file pointed to by fileUri.
         * @param fileUri the URI of the file to be processed
         */
        ProcessInputTask(final Uri fileUri) {
            mInput = null;
            mFileUri = fileUri;
        }

        /**
         * Constructor for processing input from current input reader.
         */
        ProcessInputTask() {
            mInput = null;
            mFileUri = null;
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
                // Call mListener for showing error message
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mListener != null) {
                            mListener.onInvalidInputNumber();
                        }
                    }
                });
            }
            finishInputProcessing();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onProgressUpdate();
                    }
                }
            });
        }

        /**
         * Opens the input reader from the mInputUri and initializes processing of the reader.
         */
        private void processFileInput() {
            mInputUri = mFileUri;
            try {
                InputStream stream = getActivity().getContentResolver().openInputStream(mInputUri);
                if (stream == null) {
                    throw new NullPointerException();
                }
                mInputReader = new BufferedReader(new InputStreamReader(stream));
            } catch (FileNotFoundException | NullPointerException e) {
                abortFileInput();
                return;
            }
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
                        // Call mListener for showing error message
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mListener != null) {
                                    mListener.onInvalidInputNumber();
                                }
                            }
                        });
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
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        // Abort file input processing
                        mListener.onFileInputAborted();
                    }
                }
            });
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
                mNumberType = inputNumberType;
                inputNumbers = historyNumbers.concatenate(inputNumbers);
                showClear();
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
            NumberSequence historyPredictionNumbers;
            if (mAutoDetect) {
                // Detect best generator and update all states
                int bestGenerator = mRandomManager.detectGenerator(inputNumbers, mHistoryBuffer);
                historyPredictionNumbers = mRandomManager.getIncomingPredictionNumbers();
                if (bestGenerator != mRandomManager.getCurrentGeneratorIndex()) {
                    // Set generator and process complete history
                    mRandomManager.setCurrentGeneratorIndex(bestGenerator);
                    mRandomManager.resetCurrentGenerator();
                    NumberSequence historyNumbers = new NumberSequence(mHistoryBuffer.toArray(),
                            mNumberType);
                    mRandomManager.findCurrentSequence(historyNumbers, null);
                    NumberSequence replacedNumbers = mRandomManager.getIncomingPredictionNumbers();
                    mRandomManager.findCurrentSequence(inputNumbers, mHistoryBuffer);
                    historyPredictionNumbers = mRandomManager.getIncomingPredictionNumbers();
                    // Post change to user interface
                    showGeneratorChange(historyNumbers, replacedNumbers, bestGenerator);
                }
            } else {
                mRandomManager.findCurrentSequence(inputNumbers, mHistoryBuffer);
                historyPredictionNumbers = mRandomManager.getIncomingPredictionNumbers();
            }
            // Generate new prediction without updating the state
            NumberSequence predictionNumbers = mRandomManager.predict(mPredictionLength,
                    mNumberType);
            mHistoryBuffer.put(inputNumbers.getInternalNumbers());
            // Post result to user interface
            showInputUpdate(inputNumbers, historyPredictionNumbers, predictionNumbers);
        }

        /**
         * Sends the instruction to clear to the processing mListener.
         */
        private void showClear() {
            boolean posted = mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Clear all fields
                    if (mListener != null) {
                        mListener.onClear();
                    } else {
                        mMissingUpdate = true;
                    }
                }
            });
            if (!posted) {
                mMissingUpdate = true;
            }
        }

        /**
         * Sends the generator change to the processing mListener.
         * @param historyNumbers the complete previous input
         * @param replacedNumbers the complete previous prediction numbers
         * @param bestGenerator index of the best generator
         */
        private void showGeneratorChange(final NumberSequence historyNumbers,
                                         final NumberSequence replacedNumbers,
                                         final int bestGenerator) {
            boolean posted = mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Append input numbers to history
                    if (mListener != null) {
                        mListener.onGeneratorChanged(bestGenerator);
                        mListener.onHistoryPredictionReplaced(historyNumbers, replacedNumbers);
                    } else {
                        mMissingUpdate = true;
                    }
                }
            });
            if (!posted) {
                mMissingUpdate = true;
            }
        }

        /**
         * Sends the processing result to the processing mListener.
         * @param inputNumbers the processed input numbers
         * @param historyPredictionNumbers the prediction numbers corresponding to the input
         * @param predictionNumbers the predicted numbers
         */
        private void showInputUpdate(final NumberSequence inputNumbers,
                                     final NumberSequence historyPredictionNumbers,
                                     final NumberSequence predictionNumbers) {
            boolean posted = mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Append input numbers to history
                    if (mListener != null) {
                        mListener.onHistoryChanged(inputNumbers, historyPredictionNumbers);
                        mListener.onPredictionChanged(predictionNumbers);
                    } else {
                        mMissingUpdate = true;
                    }
                }
            });
            if (!posted) {
                mMissingUpdate = true;
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
            boolean posted = mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!mMissingUpdate && mListener != null) {
                        mListener.onPredictionChanged(predictionNumbers);
                    } else {
                        mMissingUpdate = true;
                    }
                }
            });
            if (!posted) {
                mMissingUpdate = true;
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
                    String status;
                    if (mClientSocket == null || mClientSocket.isClosed()) {
                        try {
                            // Display information about the server socket
                            status = getResources().getString(R.string.server_listening) + " "
                                    + Integer.toString(mServerPort);
                            postStatus(status);
                            mClientSocket = mServerSocket.accept();
                            status = getResources().getString(R.string.client_connected);
                            postStatus(status);
                            InputStream inputStream = mClientSocket.getInputStream();
                            mInputReader = new BufferedReader(new InputStreamReader(inputStream));
                            OutputStream outputStream = mClientSocket.getOutputStream();
                            mOutputWriter = new BufferedWriter(new OutputStreamWriter(
                                    outputStream));
                        } catch (IOException e) {
                            closeClient();
                            continue;
                        }
                        boolean posted = mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                processInputSocket();
                            }
                        });
                        if (!posted) {
                            abortSocketInput();
                            return;
                        }
                    }
                    try {
                        mDisconnected.await();
                        closeClient();
                        status = getResources().getString(R.string.client_disconnected);
                        postStatus(status);
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
         * Posts the current socket status to the user interface thread.
         * @param status the status message
         */
        private void postStatus(final String status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onSocketStatusChanged(status);
                    }
                }
            });
        }

        /**
         * Aborts socket input processing and updates the user interface.
         */
        private void abortSocketInput() {
            closeSockets();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onSocketInputAborted();
                    }
                }
            });
        }
    }
}