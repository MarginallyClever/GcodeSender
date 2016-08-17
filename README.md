#GcodeSender

This program will feed instructions to an serial device one line at a time.
It waits for the ">" character between each send.

## I just want to run it!

./java/GcodeSender.jar is compiled and should run.

On most systems you can double click it to start.

On some you may need to do something like

    java -classpath RXTXcomm.jar -Djava.library.path=[path to RXTXcomm.jar] -jar DrawbotGUI.jar

##Drivers

Need Arduino code that reads GCode and moves motors?  Try https://github.com/MarginallyClever/gcodecncdemo

##More

For the latest version please visit http://www.github.com/MarginallyClever/GcodeSender

If you find this free program useful, please send me some beer money.

##Author

http://marginallyclever.com
Dan Royer
2014-01-29

