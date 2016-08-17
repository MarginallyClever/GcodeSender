package com.marginallyclever.gcodesender;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.marginallyclever.gcodesender.Generators.GcodeGenerator;
import com.marginallyclever.gcodesender.Generators.HilbertCurveGenerator;
import com.marginallyclever.gcodesender.Generators.YourMessageHereGenerator;


/**
 * GcodeSender makes a serial connection to an arduino with adafruit motor shields.  Each shield drives two stepper motors.
 * If a clock were drawn around the origin, The motors would be arranged at the 3:00, 7:00, and 11:00 positions.
 * 
 * @author danroyer
 *
 */
public class GcodeSender
extends JPanel
implements ActionListener, KeyListener, SerialConnectionReadyListener
{
	private static final long serialVersionUID=1;
	private static final String RECENT_FILES = "recent-files-";

	private static String VERSION="1.1.0";
	protected static GcodeSender singleton;
	
	// command line
	private JPanel textInputArea;
	private JTextField commandLineText;
	private JButton commandLineSend;
	
	// menus
	static private JFrame mainframe;
	private JMenuBar menuBar;
	private JMenuItem buttonOpenFile;
	private JMenuItem buttonExit;
    private JMenuItem [] buttonRecent = new JMenuItem[10];
	private JMenuItem buttonRescan;
	private JMenuItem buttonDisconnect;
	private JMenuItem buttonStart;
	private JMenuItem buttonPause;
	private JMenuItem buttonHalt;
	private JMenuItem buttonAbout;
	private JMenuItem buttonCheckForUpdate;
	private StatusBar statusBar;
	
	// serial connections
	private final SerialConnection arduino;
	private boolean aReady;
	private boolean wasConfirmed;

	// settings
	private final Preferences prefs;
	private String[] recentFiles = {"","","","","","","","","",""};
	
	public double [] len = new double[6];
	
	// files
	private boolean running;
	private boolean paused=true;
	private boolean oneAtATime;
	private boolean loopForever;
    private long linesTotal;
	private long linesProcessed;
	private boolean fileOpened;
	private ArrayList<String> gcode;
	
	// Generators
	private GcodeGenerator [] generators;
	private JMenuItem generatorButtons[];

	
	public JFrame GetMainFrame() {
		return mainframe;
	}
	
	static public GcodeSender getSingleton() {
		if(singleton==null) singleton=new GcodeSender();
		return singleton;
	}
	
	
	private GcodeSender() {
		prefs = Preferences.userRoot().node("GcodeSender");
		
		loadConfig();
		
		LoadGenerators();
		
		arduino = new SerialConnection("Arduino");
		arduino.addListener(this);
	}
	
	
	protected void LoadGenerators() {
		// TODO find the generator jar files and load them.
		generators = new GcodeGenerator[2];
		generators[0] = new HilbertCurveGenerator();
		generators[1] = new YourMessageHereGenerator();
		
		generatorButtons = new JMenuItem[2];
	}
	
	protected JMenu LoadGenerateMenu() {
		JMenu menu = new JMenu("Generate");
        menu.setEnabled(!running);
        
        for(int i=0;i<generators.length;++i) {
        	generatorButtons[i] = new JMenuItem(generators[i].GetMenuName());
        	generatorButtons[i].addActionListener(this);
        	menu.add(generatorButtons[i]);
        }
        
        return menu;
	}
	
	protected boolean GeneratorMenuAction(ActionEvent e) {
		Object subject = e.getSource();
		
        for(int i=0;i<generators.length;++i) {
        	if(subject==generatorButtons[i]) {
        		generators[i].Generate();
        		return true;
        	}
		}
		return false;
	}
	
	
	@Override
	public void keyTyped(KeyEvent e) {}

    /** Handle the key-pressed event from the text field. */
    public void keyPressed(KeyEvent e) {}

    /** Handle the key-released event from the text field. */
    public void keyReleased(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_ENTER && arduino.isPortOpened() && !running) {
			String msg = commandLineText.getText();
			sendLineToRobot(msg);
			commandLineText.setText("");
		}
	}
    
    /**
     * Check the user's preferences for how to run this session. 
     * @return false if the user hits cancel (please don't start sending)
     */
    private boolean checkStartOptions() {
		oneAtATime=true;
		
		JCheckBox checkLoopForever = new JCheckBox("Loop forever");
		JCheckBox checkOneAtATime = new JCheckBox("One line at a time");
		
		JPanel panel = new JPanel(new GridLayout(0, 1));
		panel.add(checkLoopForever);
		panel.add(checkOneAtATime);

		int result = JOptionPane.showConfirmDialog(null, panel, getName(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result == JOptionPane.OK_OPTION) {
			oneAtATime = checkOneAtATime.isSelected();
			loopForever = checkLoopForever.isSelected();

			return true;
		}
		
    	return false;
    }
    
    private void startSending() {
		if (fileOpened && checkStartOptions()) {
			paused = false;
			running = true;
			linesProcessed = 0;
			updateMenuBar();
			sendFileCommand();
		}
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		Object subject = e.getSource();

		if (subject == commandLineSend && arduino.isPortOpened() && !running) {
			String msg = commandLineText.getText();
			sendLineToRobot(msg);
			commandLineText.setText("");
		}
		if(subject==buttonExit) {
			System.exit(0);  // @TODO: be more graceful?
			return;
		}
		if(subject==buttonOpenFile) {
			openFileDialog();
			return;
		}
		if(GeneratorMenuAction(e)) {
			return;
		}
		if(subject==buttonRescan) {
			arduino.DetectSerialPorts();
			updateMenuBar();
			return;
		}
		if(subject==buttonDisconnect) {
			arduino.ClosePort();
			return;
		}
		if(subject==buttonStart) {
			oneAtATime=false;
			startSending();
			return;
		}
		if( subject == buttonPause ) {
			if(running) {
				if(paused) {
					buttonPause.setText("Pause");
					paused=false;
					// @TODO: if the robot is not ready to unpause, this might fail and the program would appear to hang.
					sendFileCommand();
				} else {
					buttonPause.setText("Unpause");
					paused=true;
				}
			}
			return;
		}
		if( subject == buttonHalt ) {
			halt();
			return;
		}
		if( subject == buttonAbout ) {
			JOptionPane.showMessageDialog(null,"<html><body>"
					+"<h1>GcodeSender v"+VERSION+"</h1>"
					+"<h3><a href='http://www.marginallyclever.com/'>http://www.marginallyclever.com/</a></h3>"
					+"<p>Created by Dan Royer (dan@marginallyclever.com).</p><br>"
					+"<p>To get the latest version please visit<br><a href='https://github.com/MarginallyClever/GcodeSender'>https://github.com/MarginallyClever/GcodeSender</a></p><br>"
					+"<p>This program is open source and free.  If this was helpful<br> to you, please buy me a thank you beer through Paypal.</p>"
					+"</body></html>");
			return;
		}
		if( subject == buttonCheckForUpdate ) {
			checkForUpdate();
			return;
		}
		
		int i;
		for(i=0;i<10;++i) {
			if(subject == buttonRecent[i]) {
				openFile(recentFiles[i]);
				return;
			}
		}
	}
	
	
	/**
	 * Parse https://github.com/MarginallyClever/Makelangelo/releases/latest redirect notice
	 * to find the latest release tag.
	 */
	public void checkForUpdate() {
		try {
			URL github = new URL("https://github.com/MarginallyClever/GCodeSender/releases/latest");
			HttpURLConnection conn = (HttpURLConnection) github.openConnection();
			conn.setInstanceFollowRedirects(false);  //you still need to handle redirect manully.
			HttpURLConnection.setFollowRedirects(false);
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));

			String inputLine;
			if ((inputLine = in.readLine()) != null) {
				// parse the URL in the text-only redirect
				String matchStart = "<a href=\"";
				String matchEnd = "\">";
				int start = inputLine.indexOf(matchStart);
				int end = inputLine.indexOf(matchEnd);
				if (start != -1 && end != -1) {
					inputLine = inputLine.substring(start + matchStart.length(), end);
					// parse the last part of the redirect URL, which contains the release tag (which is the VERSION)
					inputLine = inputLine.substring(inputLine.lastIndexOf("/") + 1);

					System.out.println("last release: " + inputLine);
					System.out.println("your VERSION: " + VERSION);

					if (inputLine.compareTo(VERSION) > 0) {
						JOptionPane.showMessageDialog(null, "A new version of this software is available.  The latest version is "+inputLine+"\n"
								+"Please visit http://www.marginallyclever.com/ to get the new hotness.");
					} else {
						JOptionPane.showMessageDialog(null, "This version is up to date.");
					}
				}
			} else {
				throw new Exception();
			}
			in.close();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "Sorry, I failed.  Please visit http://www.marginallyclever.com/ to check yourself.");
		}
	}
	
	
	/**
	 * stop sending commands to the robot.
	 * @todo add an e-stop command?
	 */
	public void halt() {
		running=false;
		paused=false;
	    linesProcessed=0;
		updateMenuBar();
	}

	
	protected boolean inSendNow;
	/**
	 * Take the next line from the file and send it to the robot, if permitted. 
	 */
	public void sendFileCommand() {
		if(inSendNow || !running || paused || !fileOpened || !arduino.isPortOpened() || linesProcessed>=linesTotal) return;
		
		inSendNow=true;
		
		String line;
		do {
			// are there any more commands?
			line=gcode.get((int)linesProcessed).trim();
			linesProcessed++;
			
			arduino.Log(">> "+line+"\n");

			// loop until we find a line that gets sent to the robot, at which point we'll
			// pause for the robot to respond.  Also stop at end of file.			
			if( oneAtATime && line.length()>0 ) {
				int n = JOptionPane.showConfirmDialog(mainframe,line,"line "+linesProcessed,JOptionPane.OK_CANCEL_OPTION);
				if(n == JOptionPane.CANCEL_OPTION) {
					halt();
					break;
				}
			}
		} while(!sendLineToRobot(line) && linesProcessed<linesTotal);
		
		if(linesProcessed==linesTotal) {
			// end of file
			if( loopForever ) {
				linesProcessed=0;
			} else {
				halt();
			}
		}
		
		inSendNow=false;
	}
	
	/**
	 * Processes a single instruction meant for the robot.
	 * @param line
	 * @return true if the command is sent to the robot.
	 */
	public boolean sendLineToRobot(String line) {
		if(line.length()==0 || line.equals(";")) return false;
		
		// tool change request?
		String [] tokens = line.split("\\s");

		if(tokens[0].startsWith("M")) {
			// tool change?
			if(Arrays.asList(tokens).contains("M06") || Arrays.asList(tokens).contains("M6")) {
				String [] tools = {"black","red","blue","green"};
				for(int i=0;i<tokens.length;++i) {
					if(tokens[i].startsWith("T")) {
						JOptionPane.showMessageDialog(null,"Please change pen to "+tools[Integer.parseInt(tokens[i].substring(1))]+" and click OK.");
					}
				}
				// still ready to send
				return false;
			}
		
			// end of program?
			if(tokens[0]=="M02" || tokens[0]=="M2") {
				halt();
				return false;
			}
		} 

		// contains a comment?  if so remove it
		int index=line.indexOf('(');
		if(index!=-1) {
			line=line.substring(0,index).trim();
			if(line.length()==0) {
				// entire line was a comment.
				return false;  // still ready to send
			}
		}

		// send relevant part of line to the robot
		arduino.SendCommand(line);
		
		return true;
	}

	protected void loadConfig() {
		getRecentFiles();
	}

	protected void saveConfig() {
		getRecentFiles();
	}
	
	private void closeFile() {
		if(fileOpened) {
			fileOpened=false;
		}
	}
	
	/**
	 * Opens a file.  If the file can be opened, get a drawing time estimate, update recent files list, and repaint the preview tab.
	 * @param filename what file to open
	 */
	public void openFile(String filename) {
		closeFile();

	    try {
			Scanner scanner = new Scanner(new FileInputStream(filename), "UTF-8");
	    	linesTotal=0;
	    	gcode = new ArrayList<String>();
		    try {
		      while (scanner.hasNextLine()) {
		    	  gcode.add(scanner.nextLine());
		    	  ++linesTotal;
		      }
		    }
		    finally{
		      scanner.close();
		    }
	    }
	    catch(IOException e) {
	    	removeRecentFile(filename);
	    	return;
	    }

	    fileOpened=true;
	   	updateRecentFiles(filename);

	    halt();
	}
	
	
	/**
	 * changes the order of the recent files list in the File submenu, saves the updated prefs, and refreshes the menus.
	 * @param filename the file to push to the top of the list.
	 */
	public void updateRecentFiles(String filename) {
		int cnt = recentFiles.length;
		String [] newFiles = new String[cnt];
		
		newFiles[0]=filename;
		
		int i;
		int j=1;
		for(i=0;i<cnt;++i) {
			if(!filename.equals(recentFiles[i]) && recentFiles[i] != "") {
				newFiles[j] = recentFiles[i];
				j++;
				if(j == cnt ) break;
			}
		}

		recentFiles=newFiles;

		// update prefs
		for(i=0;i<cnt;++i) {
			if( recentFiles[i]==null ) recentFiles[i] = "";
			if( !recentFiles[i].isEmpty() ) {
				prefs.put(RECENT_FILES+i, recentFiles[i]);
			}
		}
		
		updateMenuBar();
	}
	
	// A file failed to load.  Remove it from recent files, refresh the menu bar.
	public void removeRecentFile(String filename) {
		int i;
		for(i=0;i<recentFiles.length-1;++i) {
			if(recentFiles[i]==filename) {
				break;
			}
		}
		for(;i<recentFiles.length-1;++i) {
			recentFiles[i]=recentFiles[i+1];
		}
		recentFiles[recentFiles.length-1]="";

		// update prefs
		for(i=0;i<recentFiles.length;++i) {
			if(!recentFiles[i].isEmpty()) {
				prefs.put(RECENT_FILES+i, recentFiles[i]);
			}
		}
		
		updateMenuBar();
	}
	
	// Load recent files from prefs
	public void getRecentFiles() {
		int i;
		for(i=0;i<recentFiles.length;++i) {
			recentFiles[i] = prefs.get(RECENT_FILES+i, recentFiles[i]);
		}
	}
	
	// creates a file open dialog. If you don't cancel it opens that file.
	public void openFileDialog() {
	    // Note: source for ExampleFileFilter can be found in FileChooserDemo,
	    // under the demo/jfc directory in the Java 2 SDK, Standard Edition.
		String filename = (recentFiles[0].length()>0) ? filename=recentFiles[0] : "";

		FileFilter filterImage  = new FileNameExtensionFilter("Images (jpg/bmp/png/gif)", "jpg", "jpeg", "png", "wbmp", "bmp", "gif");
		FileFilter filterGCODE = new FileNameExtensionFilter("GCODE files (ngc)", "ngc");
		 
		JFileChooser fc = new JFileChooser(new File(filename));
		fc.addChoosableFileFilter(filterImage);
		fc.addChoosableFileFilter(filterGCODE);
	    if(fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
	    	openFile(fc.getSelectedFile().getAbsolutePath());
	    }
	}
	
	private void updateMenuBar() {
		JMenu menu;
        JMenu subMenu;
        
        if(menuBar != null) menuBar.removeAll();

        menu = new JMenu("GCodeSender");
        
        buttonAbout = new JMenuItem("About",KeyEvent.VK_A);
        buttonAbout.getAccessibleContext().setAccessibleDescription("About this program");
        buttonAbout.addActionListener(this);
        menu.add(buttonAbout);

        
        buttonCheckForUpdate = new JMenuItem("Check for update",KeyEvent.VK_A);
        buttonCheckForUpdate.addActionListener(this);
        menu.add(buttonCheckForUpdate);

        buttonExit = new JMenuItem("Exit",KeyEvent.VK_Q);
        buttonExit.getAccessibleContext().setAccessibleDescription("Goodbye...");
        buttonExit.addActionListener(this);
        menu.add(buttonExit);
        
        menuBar.add(menu);
        
        // connection menu
        menu = new JMenu("Connection");
        menu.setMnemonic(KeyEvent.VK_T);
        menu.getAccessibleContext().setAccessibleDescription("Connection settings.");
        menu.setEnabled(!running);
        
        subMenu = arduino.getBaudMenu();
        subMenu.setText("Speed");
        menu.add(subMenu);
        
        subMenu = arduino.getPortMenu();
        subMenu.setText("Port");
        menu.add(subMenu);

        buttonRescan = new JMenuItem("Rescan Ports",KeyEvent.VK_N);
        buttonRescan.getAccessibleContext().setAccessibleDescription("Rescan the available ports.");
        buttonRescan.addActionListener(this);
        menu.add(buttonRescan);

        menu.addSeparator();
        
        buttonDisconnect = new JMenuItem("Disconnect",KeyEvent.VK_A);
        buttonDisconnect.addActionListener(this);
        menu.add(buttonDisconnect);
        
        menuBar.add(menu);

		
        // file menu.
        menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);
        menu.setEnabled(!running);
        menuBar.add(menu);
 
        buttonOpenFile = new JMenuItem("Open File...",KeyEvent.VK_O);
        buttonOpenFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.ALT_MASK));
        buttonOpenFile.getAccessibleContext().setAccessibleDescription("Open a g-code file...");
        buttonOpenFile.addActionListener(this);
        menu.add(buttonOpenFile);

        menu.addSeparator();

        // list recent files
        getRecentFiles();
        if(recentFiles.length>0) {
        	// list files here
        	int i;
        	for(i=0;i<recentFiles.length;++i) {
        		if(recentFiles[i].length()==0) break;
            	buttonRecent[i] = new JMenuItem((1+i) + " "+recentFiles[i],KeyEvent.VK_1+i);
            	if(buttonRecent[i]!=null) {
            		buttonRecent[i].addActionListener(this);
            		menu.add(buttonRecent[i]);
            	}
        	}
        }

        menuBar.add(menu);

        menu = LoadGenerateMenu();

        menuBar.add(menu);
        
        // action menu
        menu = new JMenu("Action");
        menu.setMnemonic(KeyEvent.VK_A);
        menu.setEnabled(arduino.isPortOpened() && fileOpened);
        
        buttonStart = new JMenuItem("Start",KeyEvent.VK_S);
        buttonStart.getAccessibleContext().setAccessibleDescription("Start sending g-code");
        buttonStart.addActionListener(this);
    	buttonStart.setEnabled(!running);
        menu.add(buttonStart);

        buttonPause = new JMenuItem("Pause",KeyEvent.VK_P);
        buttonPause.getAccessibleContext().setAccessibleDescription("Pause sending g-code");
        buttonPause.addActionListener(this);
        buttonPause.setEnabled(running);
        menu.add(buttonPause);

        buttonHalt = new JMenuItem("Halt",KeyEvent.VK_H);
        buttonHalt.getAccessibleContext().setAccessibleDescription("Halt sending g-code");
        buttonHalt.addActionListener(this);
        buttonHalt.setEnabled(running);
        menu.add(buttonHalt);

        menuBar.add(menu);
        
        // finish
        menuBar.updateUI();
	}
	
	public JMenuBar createMenuBar() {
        // If the menu bar exists, empty it.  If it doesn't exist, create it.
        menuBar = new JMenuBar();

        updateMenuBar();
        
        return menuBar;
	}
	
	public JPanel getTextInputField() {
		textInputArea = new JPanel();
		textInputArea.setLayout(new BoxLayout(textInputArea,BoxLayout.LINE_AXIS));
		
		commandLineText = new JTextField(1);
		commandLineSend = new JButton("Send");
		
		textInputArea.add(commandLineText);
		textInputArea.add(commandLineSend);
		
		commandLineText.addKeyListener(this);
		commandLineSend.addActionListener(this);
		
		return textInputArea;
	}
	
	private Container createContentPane() {
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setOpaque(true);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.add(getTextInputField());
        split.add(arduino.getGUI());
        split.setDividerSize(0);
        
        contentPane.add(split,BorderLayout.CENTER);
        
        statusBar = new StatusBar();
        contentPane.add(statusBar, java.awt.BorderLayout.SOUTH);
		statusBar.setMessage("");
        
        return contentPane;
	}
    
    // Create the GUI and show it.  For thread safety, this method should be invoked from the event-dispatching thread.
    private static void createAndShowGUI() {
        //Create and set up the window.
    	mainframe = new JFrame("GcodeSender");
        mainframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
 
        //Create and set up the content pane.
        GcodeSender demo = GcodeSender.getSingleton();
        mainframe.setJMenuBar(demo.createMenuBar());
        mainframe.setContentPane(demo.createContentPane());
 
        //Display the window.
        mainframe.setSize(1100,500);
        mainframe.setVisible(true);
    }
    
    public static void main(String[] args) {
	    //Schedule a job for the event-dispatching thread:
	    //creating and showing this application's GUI.
	    javax.swing.SwingUtilities.invokeLater(new Runnable() {
	        public void run() {
	            createAndShowGUI();
	        }
	    });
    }

	@Override
	public void lineError(SerialConnection arg0, int lineNumber) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void connectionReady(SerialConnection arg0) {
		sendNow(arg0);
	}

	@Override
	public void dataAvailable(SerialConnection arg0, String data) {
		arduino.Log("** "+data+"\n");
		sendNow(arg0);
	}
	
	void sendNow(SerialConnection arg0) {
		if(arg0==arduino) aReady=true;
		
		if(aReady) {
			if(!wasConfirmed) {
				wasConfirmed=true;
				updateMenuBar();
			}
			aReady=false;
			sendFileCommand();
		}
	}
}
