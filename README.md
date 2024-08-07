Derandom
========

Predicts pseudo random numbers based on a sequence of observed numbers.


Usage
-----

Enter a sequence of numbers that you obtained from a pseudo random number
generator like, for instance, the Java standard pseudo random number
generator or the Mersenne Twister MT19937.  The app will then try to
predict following numbers from the generator.

The app expects all numbers to be entered as integers or floating point
numbers between zero and one.  Currently, floating point numbers are
supported for the Mersenne Twister only.  Three input modes are
supported:

1. *Text field* lets you enter the numbers directly on the device.
2. *File* lets you choose a file with newline separated number strings.
3. *Socket* opens a server socket on the device.  You can then connect
with a custom client by means of a client socket and send newline
separated number strings to the server.  After each number the server
will send back the next newline separated predictions.  Each block of
predictions is separated by an additional newline.

To test the app, enter the following numbers in the *Text field*:
```
1412437139
1552322984
168467398
1111755060
-928874005
```
These numbers were sampled from the Java linear congruential generator
`Random.nextInt()`.  Thus, the app should detect `LCG: Java` after the
third number input, and numbers in the prediction history should appear
in green instead of red, indicating that those numbers were correctly
predicted.

The following Python program can be used to test socket input.  The
program samples numbers from the standard Python pseudo random number
generator and sends them to a network socket:
```python
import random
import socket

HOST = "localhost"  # Host of Android device
PORT = 6869  # Default Derandom port
with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    s.connect((HOST, PORT))
    buffer = s.makefile()  # Buffer for readline
    for _ in range(0, 700):
        # Sample bits from generator
        bits = random.getrandbits(32)
        # Send number string
        message = str(bits) + "\n"
        s.sendall(message.encode())
        # Read and print predictions
        for _ in range(0, 9):  # 8 predictions and newline
            line = buffer.readline()
            print(line, end="")
```
Start the app on the Android device and set the input spinner from
*Text field* to *Socket*.  Make sure that the device and the Derandom
socket port (default 6869) are reachable in your network.  Then set
`HOST` in the Python program to the address of your Android device and
run the program.  For each number that is sent by the Python program,
eight predictions are returned by Derandom and displayed by the Python
program.  After the app has received 624 numbers the Python Mersenne
Twister should be detected and, in the app, numbers in the prediction
history should appear in green instead of red.  You can also replace
`random.getrandbits(32)` with `random.random()` and send 1300 numbers
instead of 700 numbers to account for unobserved bits.


Building from source
--------------------

Define SDK location with `sdk.dir` in the `local.properties` file or with
an `ANDROID_HOME` environment variable.  Then type the following command
to build in release mode:
```shell
./gradlew assembleRelease
```


License
-------

```text
Copyright (C) 2015-2024 Arno Onken

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
