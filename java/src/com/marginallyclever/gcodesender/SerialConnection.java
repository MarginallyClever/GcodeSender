package com.marginallyclever.gcodesender;


import jssc.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.marginallyclever.gcodesender.SerialConnectionReadyListener;

public class SerialConnection
implements SerialPortEventListener, ActionListener {
	private static final String CUE = "> ";
	private static final String LAST_PORT = "last port";
	private static final String NOCHECKSUM = "NOCHECKSUM ";
	private static final String BADCHECKSUM = "BADCHECKSUM ";
	private static final String BADLINENUM = "BADLINENUM ";
	private static final String NEWLINE = "\n";
	private static final String COMMENT_START = ";";

	
	private String[] portsDetected;
	
	public static String BAUD_RATE = "57600";
	private static String [] baudsAllowed = { "300","1200","2400","4800","9600","14400","19200","28800","38400","57600","115200","125000","250000"};
	
	public SerialPort serialPort;
	public boolean portOpened;

	public String portName;
	public boolean waitingForCue=true;
	
	// settings
	private final Preferences prefs;
	
	// menus & GUIs
	private final JTextArea log = new JTextArea();
	private JScrollPane logPane;
    private JMenuItem [] buttonPorts;
    private JMenuItem [] buttonBauds;
    
    // communications
	private String inputBuffer;
	private final  ArrayList<String> commandQueue = new ArrayList<String>();

    // Listeners which should be notified of a change to the percentage.
    private final ArrayList<SerialConnectionReadyListener> listeners = new ArrayList<SerialConnectionReadyListener>();

	
	public SerialConnection(String name) {
		prefs = Preferences.userRoot().node("SerialConnection").node(name);
		
		DetectSerialPorts();

		OpenPort(GetLastPort());
	}
	
	public void finalize() {
		ClosePort();
	}
	
	private String GetLastPort(){
		return prefs.get(LAST_PORT,"");
	}
	
	private void SetLastPort(String portName) {
		prefs.put(LAST_PORT, portName);
	}
	
	private String GetLastBaud() {
		return prefs.get("last baud",BAUD_RATE);
	}
	
	private void SetLastBaud(String baud) {
		prefs.put(LAST_PORT, baud);		
	}
	
	public void Log(String msg) {
		log.append(msg);
		log.setCaretPosition(log.getText().length());
	}
	
	@Override
	public void serialEvent(SerialPortEvent events) {
		String rawInput;
		String oneLine;
		int x;
		
        if(events.isRXCHAR()) {
        	if(!portOpened) return;
            try {
            	int len = events.getEventValue();
				byte [] buffer = serialPort.readBytes(len);
				if( len>0 ) {
					rawInput = new String(buffer,0,len, StandardCharsets.UTF_8);
					inputBuffer+=rawInput;
					// each line ends with a \n.
					for( x=inputBuffer.indexOf("\n"); x!=-1; x=inputBuffer.indexOf("\n") ) {
						x=x+1;
						oneLine = inputBuffer.substring(0,x);
						inputBuffer = inputBuffer.substring(x);

						// check for error
						int errorLine = errorReported(oneLine);
	                    if(errorLine != -1) {
							notifyLineError(errorLine);
	                    } else {
	                    	// no error
	                    	{
	                    		notifyDataAvailable(oneLine);
	                    	}
	                    }
	                    
	                    // wait for the cue to send another command
						if(oneLine.indexOf(CUE)==0) {
							waitingForCue=false;
						}
					}
					if(!waitingForCue) {
						sendQueuedCommand();
					}
				}
            } catch (SerialPortException e) {}
        }
	}

	private void notifyLineError(int lineNumber) {
	      for (SerialConnectionReadyListener listener : listeners) {
	          listener.lineError(this,lineNumber);
	        }
	}
	
    private void notifyConnectionReady() {
      for (SerialConnectionReadyListener listener : listeners) {
        listener.connectionReady(this);
      }
    }
	
	// tell all listeners data has arrived
	private void notifyDataAvailable(String line) {
	      for (SerialConnectionReadyListener listener : listeners) {
	        listener.dataAvailable(this,line);
	      }
	}


	/**
	 * Check if the robot reports an error and if so what line number.
	 *
	 * @return -1 if there was no error, otherwise the line number containing the error.
	 */
	protected int errorReported(String line) {
		if (line.lastIndexOf(NOCHECKSUM) != -1) {
			String afterError = line.substring(line.lastIndexOf(NOCHECKSUM) + NOCHECKSUM.length());
			String x = getNumberPortion(afterError);
			int err = 0;
			try {
				err = Integer.decode(x);
			} catch (Exception e) {
			}

			return err;
		}
		if (line.lastIndexOf(BADCHECKSUM) != -1) {
			String afterError = line.substring(line.lastIndexOf(BADCHECKSUM) + BADCHECKSUM.length());
			String x = getNumberPortion(afterError);
			int err = 0;
			try {
				err = Integer.decode(x);
			} catch (Exception e) {
			}

			return err;
		}
		if (line.lastIndexOf(BADLINENUM) != -1) {
			String afterError = line.substring(line.lastIndexOf(BADLINENUM) + BADLINENUM.length());
			String x = getNumberPortion(afterError);
			int err = 0;
			try {
				err = Integer.decode(x);
			} catch (Exception e) {
			}

			return err;
		}

		return -1;
	}
	/**
	 * Java string to int is very picky.  this method is slightly less picky.  Only works with positive whole numbers.
	 *
	 * @param src
	 * @return the portion of the string that is actually a number
	 */
	private String getNumberPortion(String src) {
		src = src.trim();
		int length = src.length();
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < length; i++) {
			Character character = src.charAt(i);
			if (Character.isDigit(character)) {
				result.append(character);
			}
		}
		return result.toString();
	}
	
	protected void sendQueuedCommand() {
		if(!portOpened || waitingForCue) return;
		
		if(commandQueue.size()==0) {
			notifyConnectionReady();
		    return;
		}
		
		String command;
		try {
			command=commandQueue.remove(0);
			if(!command.endsWith("\n")) command+="\n";
			
			serialPort.writeBytes(command.getBytes(StandardCharsets.UTF_8));
			waitingForCue=true;
		}
		catch(IndexOutOfBoundsException e1) {}
		catch(SerialPortException e2) {}
	}
	
	public void SendCommand(String command) {
		if(!portOpened) return;
		
		commandQueue.add(command);
		sendQueuedCommand();
	}
	
	// Find all available serial ports for the settings->ports menu.
	public void DetectSerialPorts() {
	    String OS = System.getProperty("os.name").toLowerCase();

	    if (OS.indexOf("mac") >= 0) {
	      portsDetected = SerialPortList.getPortNames("/dev/");
	      //System.out.println("OS X");
	    } else if (OS.indexOf("win") >= 0) {
	      portsDetected = SerialPortList.getPortNames("COM");
	      //System.out.println("Windows");
	    } else if (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0) {
	      portsDetected = SerialPortList.getPortNames("/dev/");
	      //System.out.println("Linux/Unix");
	    } else {
	      System.out.println("OS ERROR");
	      System.out.println("OS NAME=" + System.getProperty("os.name"));
	    }
	}
	
	public boolean PortExists(String portName) {
		if(portName==null || portName.equals("")) return false;

		int i;
		for(i=0;i<portsDetected.length;++i) {
			if(portName.equals(portsDetected[i])) {
				return true;
			}
		}
		
		return false;
	}
	
	public void ClosePort() {
		if(!portOpened) return;
		
	    if (serialPort != null) {
	        try {
		        // Close the port.
		        serialPort.removeEventListener();
		        serialPort.closePort();
	        } catch (SerialPortException e) {
	            // Don't care
	        }
	    }

		portOpened=false;
	}
	
	// open a serial connection to a device.  We won't know it's the robot until  
	public int OpenPort(String portName) {
		if(portOpened && portName.equals(GetLastPort())) return 0;
		if(!PortExists(portName)) return 0;
		
		ClosePort();
		
		Log("Connecting to "+portName+"..."+NEWLINE);

		// open the port
		try {
			serialPort = new SerialPort(portName);
            serialPort.openPort();// Open serial port
            int baud = Integer.parseInt(GetLastBaud());
            serialPort.setParams(baud,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
            serialPort.addEventListener(this);
        } catch (SerialPortException e) {
			Log("<span style='color:red'>Port could not be configured:"+e.getMessage()+"</span>\n");
			return 3;
		}
		portOpened=true;
		SetLastPort(portName);
		Log("Connected.");

		return 0;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		Object subject = e.getSource();
		
		int i;
		for(i=0;i<portsDetected.length;++i) {
			if(subject == buttonPorts[i]) {
				OpenPort(portsDetected[i]);
				return;
			}
		}
		
		for(i=0;i<baudsAllowed.length;++i) {
			if(subject == buttonBauds[i]) {
				SetLastBaud(baudsAllowed[i]);
				return;
			}
		}
	}

    // Adds a listener that should be notified.
    public void addListener(SerialConnectionReadyListener listener) {
      listeners.add(listener);
    }

	public JMenu getBaudMenu() {
		JMenu subMenu = new JMenu();
	    ButtonGroup group = new ButtonGroup();
	    buttonBauds = new JRadioButtonMenuItem[baudsAllowed.length];
	    
	    String lastBaud=GetLastBaud();
	    
		int i;
	    for(i=0;i<baudsAllowed.length;++i) {
	    	buttonBauds[i] = new JRadioButtonMenuItem(baudsAllowed[i]);
	        if(lastBaud.equals(baudsAllowed[i])) buttonBauds[i].setSelected(true);
	        buttonBauds[i].addActionListener(this);
	        group.add(buttonBauds[i]);
	        subMenu.add(buttonBauds[i]);
	    }
	    
	    return subMenu;
	}

	public JMenu getPortMenu() {
		JMenu subMenu = new JMenu();
	    ButtonGroup group = new ButtonGroup();
	    buttonPorts = new JRadioButtonMenuItem[portsDetected.length];
	    
	    String lastPort=GetLastPort();
	    
		int i;
	    for(i=0;i<portsDetected.length;++i) {
	    	buttonPorts[i] = new JRadioButtonMenuItem(portsDetected[i]);
	        if(lastPort.equals(portsDetected[i])) buttonPorts[i].setSelected(true);
	        buttonPorts[i].addActionListener(this);
	        group.add(buttonPorts[i]);
	        subMenu.add(buttonPorts[i]);
	    }
	    
	    return subMenu;
	}
	

	public Component getGUI() {
	    // the log panel
	    log.setEditable(false);
	    log.setForeground(Color.GREEN);
	    log.setBackground(Color.BLACK);
	    logPane = new JScrollPane(log);
	    
	    return logPane;
	}
	
	public boolean isPortOpened() {
		return portOpened;
	}

}
