package GcodeSender;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JButton;
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

import GcodeSender.Generators.GcodeGenerator;
import GcodeSender.Generators.HilbertCurveGenerator;
import GcodeSender.Generators.YourMessageHereGenerator;


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
	static final long serialVersionUID=1;
	static final String version="1";
	
	static protected GcodeSender singleton=null;
	
	// command line
	JPanel textInputArea;
	JTextField commandLineText;
	JButton commandLineSend;
	
	// menus
	static private JFrame mainframe;
	private JMenuBar menuBar;
	private JMenuItem buttonOpenFile, buttonExit;
    private JMenuItem [] buttonRecent = new JMenuItem[10];
	private JMenuItem buttonRescan, buttonDisconnect;
	private JMenuItem buttonStart, buttonOneAtATime, buttonPause, buttonHalt;
	private JMenuItem buttonAbout,buttonCheckForUpdate;
	private StatusBar statusBar;
	
	// serial connections
	private SerialConnection arduino;
	private boolean aReady=false, wasConfirmed=false;

	// settings
	private Preferences prefs;
	private String[] recentFiles = {"","","","","","","","","",""};
	
	public double [] len = new double[6];
	
	// files
	private boolean running=false;
	private boolean paused=true;
	private boolean oneAtATime=false;
    private long linesTotal=0;
	private long linesProcessed=0;
	private boolean fileOpened=false;
	private ArrayList<String> gcode;
	
	// Generators
	GcodeGenerator [] generators;
	JMenuItem generatorButtons[];

	
	public JFrame GetMainFrame() {
		return mainframe;
	}
	
	static public GcodeSender getSingleton() {
		if(singleton==null) singleton=new GcodeSender();
		return singleton;
	}
	
	
	private GcodeSender() {
		prefs = Preferences.userRoot().node("GcodeSender");
		
		LoadConfig();
		
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
	
	
	public void SerialConnectionReady(SerialConnection arg0) {
		if(arg0==arduino) aReady=true;
		
		if(aReady) {
			if(!wasConfirmed) {
				wasConfirmed=true;
				UpdateMenuBar();
			}
			aReady=false;
			SendFileCommand();
		}
	}
	
	@Override
	public void keyTyped(KeyEvent e) {}

    /** Handle the key-pressed event from the text field. */
    public void keyPressed(KeyEvent e) {}

    /** Handle the key-released event from the text field. */
    public void keyReleased(KeyEvent e) {
		if(e.getKeyCode() == KeyEvent.VK_ENTER) {
			if(IsConfirmed() && !running) {
				arduino.SendCommand(commandLineText.getText());
				commandLineText.setText("");
			}
		}
	}
    
    private void StartDrawing() {
    	//if(fileOpened) OpenFile(recentFiles[0]);
		if(fileOpened) {
			paused=false;
			running=true;
			linesProcessed=0;
			UpdateMenuBar();
			//previewPane.setRunning(running);
			//previewPane.setLinesProcessed(linesProcessed);
			//statusBar.Start();
			SendFileCommand();
		}
    }
	
	@Override
	public void actionPerformed(ActionEvent e) {
		Object subject = e.getSource();

		if(subject==commandLineSend) {
			if(IsConfirmed() && !running) {
				arduino.SendCommand(commandLineText.getText());
				commandLineText.setText("");
			}
		}
		if(subject==buttonExit) {
			System.exit(0);  // @TODO: be more graceful?
			return;
		}
		if(subject==buttonOpenFile) {
			OpenFileDialog();
			return;
		}
		if(GeneratorMenuAction(e)) {
			return;
		}
		if(subject==buttonRescan) {
			arduino.DetectSerialPorts();
			UpdateMenuBar();
			return;
		}
		if(subject==buttonDisconnect) {
			arduino.ClosePort();
			return;
		}
		if(subject==buttonStart) {
			oneAtATime=false;
			StartDrawing();
			return;
		}
		if(subject==buttonOneAtATime) {
			oneAtATime=true;
			StartDrawing();
			return;
		}
		if( subject == buttonPause ) {
			if(running) {
				if(paused==true) {
					buttonPause.setText("Pause");
					paused=false;
					// @TODO: if the robot is not ready to unpause, this might fail and the program would appear to hang.
					SendFileCommand();
				} else {
					buttonPause.setText("Unpause");
					paused=true;
				}
			}
			return;
		}
		if( subject == buttonHalt ) {
			Halt();
			return;
		}
		if( subject == buttonAbout ) {
			JOptionPane.showMessageDialog(null,"<html><body>"
					+"<h1>GcodeSender v"+version+"</h1>"
					+"<h3><a href='http://www.marginallyclever.com/'>http://www.marginallyclever.com/</a></h3>"
					+"<p>Created by Dan Royer (dan@marginallyclever.com).</p><br>"
					+"<p>To get the latest version please visit<br><a href='https://github.com/MarginallyClever/GcodeSender'>https://github.com/MarginallyClever/GcodeSender</a></p><br>"
					+"<p>This program is open source and free.  If this was helpful<br> to you, please buy me a thank you beer through Paypal.</p>"
					+"</body></html>");
			return;
		}
		if( subject == buttonCheckForUpdate ) {
			CheckForUpdate();
			return;
		}
		
		int i;
		for(i=0;i<10;++i) {
			if(subject == buttonRecent[i]) {
				OpenFile(recentFiles[i]);
				return;
			}
		}
	}
	
	
	public void CheckForUpdate() {
		try {
		    // Get Github info?
			URL github = new URL("https://www.marginallyclever.com/other/software-update-check.php?id=2");
	        BufferedReader in = new BufferedReader(new InputStreamReader(github.openStream()));

	        String inputLine;
	        if((inputLine = in.readLine()) != null) {
	        	if( inputLine.compareTo(version) !=0 ) {
	        		JOptionPane.showMessageDialog(null,"A new version of this software is available.  The latest version is "+inputLine+"\n"
	        											+"Please visit http://www.marginallyclever.com/ to get the new hotness.");
	        	} else {
	        		JOptionPane.showMessageDialog(null,"This version is up to date.");
	        	}
	        } else {
	        	throw new Exception();
	        }
	        in.close();
		} catch (Exception e) {
    		JOptionPane.showMessageDialog(null,"Sorry, I failed.  Please visit http://www.marginallyclever.com/ to check yourself.");
		}
	}
	
	
	/**
	 * stop sending commands to the robot.
	 * @todo add an e-stop command?
	 */
	public void Halt() {
		running=false;
		paused=false;
	    linesProcessed=0;
	    //previewPane.setLinesProcessed(0);
		//previewPane.setRunning(running);
		UpdateMenuBar();
	}

	
	protected boolean inSendNow=false;
	/**
	 * Take the next line from the file and send it to the robot, if permitted. 
	 */
	public void SendFileCommand() {
		if(inSendNow || running==false || paused==true || fileOpened==false || IsConfirmed()==false || linesProcessed>=linesTotal) return;
		
		inSendNow=true;
		
		String line;
		do {
			// are there any more commands?
			line=gcode.get((int)linesProcessed++).trim();
			//previewPane.setLinesProcessed(linesProcessed);
			//statusBar.SetProgress(linesProcessed, linesTotal);
			// loop until we find a line that gets sent to the robot, at which point we'll
			// pause for the robot to respond.  Also stop at end of file.			
			if( oneAtATime && line.length()>0 ) {
				int n = JOptionPane.showConfirmDialog(mainframe,line,"line "+linesProcessed,JOptionPane.OK_CANCEL_OPTION);
				if(n == JOptionPane.CANCEL_OPTION) {
					Halt();
					break;
				}
			}
		} while(!SendLineToRobot(line) && linesProcessed<linesTotal);
		
		if(linesProcessed==linesTotal) {
			// end of file
			Halt();
		}
		
		inSendNow=false;
	}
	
	/**
	 * Processes a single instruction meant for the robot.
	 * @param line
	 * @return true if the command is sent to the robot.
	 */
	public boolean SendLineToRobot(String line) {
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
				Halt();
				return false;
			}
		} 

		// contains a comment?  if so remove it
		int index=line.indexOf('(');
		if(index!=-1) {
			//String comment=line.substring(index+1,line.lastIndexOf(')'));
			//Log("* "+comment+NL);
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

	protected void LoadConfig() {
		GetRecentFiles();
	}

	protected void SaveConfig() {
		GetRecentFiles();
	}
	
	private void CloseFile() {
		if(fileOpened==true) {
			fileOpened=false;
		}
	}
	
	/**
	 * Opens a file.  If the file can be opened, get a drawing time estimate, update recent files list, and repaint the preview tab.
	 * @param filename what file to open
	 */
	public void OpenFile(String filename) {
		CloseFile();

	    try {
	    	Scanner scanner = new Scanner(new FileInputStream(filename));
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
	    	RemoveRecentFile(filename);
	    	return;
	    }
	    
	    //previewPane.setGCode(gcode);
	    fileOpened=true;
	   	UpdateRecentFiles(filename);

	   	//EstimateDrawTime();
	    Halt();
	}
	
	
	/**
	 * changes the order of the recent files list in the File submenu, saves the updated prefs, and refreshes the menus.
	 * @param filename the file to push to the top of the list.
	 */
	public void UpdateRecentFiles(String filename) {
		int cnt = recentFiles.length;
		String [] newFiles = new String[cnt];
		
		newFiles[0]=filename;
		
		int i,j=1;
		for(i=0;i<cnt;++i) {
			if(!filename.equals(recentFiles[i]) && recentFiles[i] != "") {
				newFiles[j++] = recentFiles[i];
				if(j == cnt ) break;
			}
		}

		recentFiles=newFiles;

		// update prefs
		for(i=0;i<cnt;++i) {
			if( recentFiles[i]==null ) recentFiles[i] = new String("");
			if( recentFiles[i].isEmpty()==false ) {
				prefs.put("recent-files-"+i, recentFiles[i]);
			}
		}
		
		UpdateMenuBar();
	}
	
	// A file failed to load.  Remove it from recent files, refresh the menu bar.
	public void RemoveRecentFile(String filename) {
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
				prefs.put("recent-files-"+i, recentFiles[i]);
			}
		}
		
		UpdateMenuBar();
	}
	
	// Load recent files from prefs
	public void GetRecentFiles() {
		int i;
		for(i=0;i<recentFiles.length;++i) {
			recentFiles[i] = prefs.get("recent-files-"+i, recentFiles[i]);
		}
	}
	
	// creates a file open dialog. If you don't cancel it opens that file.
	public void OpenFileDialog() {
	    // Note: source for ExampleFileFilter can be found in FileChooserDemo,
	    // under the demo/jfc directory in the Java 2 SDK, Standard Edition.
		String filename = (recentFiles[0].length()>0) ? filename=recentFiles[0] : "";

		FileFilter filterImage  = new FileNameExtensionFilter("Images (jpg/bmp/png/gif)", "jpg", "jpeg", "png", "wbmp", "bmp", "gif");
		FileFilter filterGCODE = new FileNameExtensionFilter("GCODE files (ngc)", "ngc");
		 
		JFileChooser fc = new JFileChooser(new File(filename));
		fc.addChoosableFileFilter(filterImage);
		fc.addChoosableFileFilter(filterGCODE);
	    if(fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
	    	OpenFile(fc.getSelectedFile().getAbsolutePath());
	    }
	}
	
	private boolean IsConfirmed() {
		return arduino.portConfirmed;
	}
	
	private void UpdateMenuBar() {
		JMenu menu;
        JMenu subMenu;
        
        menuBar.removeAll();

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
        GetRecentFiles();
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
        menu.setEnabled(IsConfirmed() && fileOpened);

        buttonStart = new JMenuItem("Start",KeyEvent.VK_S);
        buttonStart.getAccessibleContext().setAccessibleDescription("Start sending g-code");
        buttonStart.addActionListener(this);
    	buttonStart.setEnabled(!running);
        menu.add(buttonStart);

        buttonOneAtATime = new JMenuItem("Start Debug",KeyEvent.VK_S);
        buttonOneAtATime.getAccessibleContext().setAccessibleDescription("Start sending g-code one line at a time");
        buttonOneAtATime.addActionListener(this);
        buttonOneAtATime.setEnabled(!running);
        menu.add(buttonOneAtATime);

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
	
	public JMenuBar CreateMenuBar() {
        // If the menu bar exists, empty it.  If it doesn't exist, create it.
        menuBar = new JMenuBar();

        UpdateMenuBar();
        
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
	
	private Container CreateContentPane() {
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
    private static void CreateAndShowGUI() {
        //Create and set up the window.
    	mainframe = new JFrame("GcodeSender");
        mainframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
 
        //Create and set up the content pane.
        GcodeSender demo = GcodeSender.getSingleton();
        mainframe.setJMenuBar(demo.CreateMenuBar());
        mainframe.setContentPane(demo.CreateContentPane());
 
        //Display the window.
        mainframe.setSize(1100,500);
        mainframe.setVisible(true);
    }
    
    public static void main(String[] args) {
	    //Schedule a job for the event-dispatching thread:
	    //creating and showing this application's GUI.
	    javax.swing.SwingUtilities.invokeLater(new Runnable() {
	        public void run() {
	            CreateAndShowGUI();
	        }
	    });
    }
}
