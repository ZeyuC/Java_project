package widgets;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.OptionalDataException;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.CellRendererPane;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import chat.Failure;
import chat.Vocabulary;
import models.AuthorListFilter;
import models.Message;
import models.Message.MessageOrder;
import models.NameSetListModel;
import java.awt.ComponentOrientation;
import javax.swing.JToggleButton;
import javax.swing.Box;

/**
 * Chat GUI v2.0 with
 * <ul>
 * <li>server message display based on {@link Message} objects rather than just
 * text but still with a different color for each user.</li>
 * <li>A text field to send new messages to the server</li>
 * <li>A List of all users which have sent a message drawn with their respective
 * color (by using a {@link ColorTextRenderer}). Selections in this list can be
 * used to filter messages</li>
 * </ul>
 * @author davidroussel
 */
public class ClientFrame2 extends AbstractClientFrame
{
	/**
	 * Serial ID (because {@link TransferHandler} is serializable)
	 */
	private static final long serialVersionUID = -7278574480208850744L;

	/**
	 * user's name (used to initialize content in the users list)
	 */
	private String clientName;

	/**
	 * List of all received messages
	 */
	private List<Message> messages;
	
	private List<String> messages_user = new Vector<String>();
	/**
	 * Object input stream. Used to read {@link Message}s on the
	 * {@link AbstractClientFrame#inPipe} and display these messages in the
	 * {@link AbstractClientFrame#document}
	 */
	private ObjectInputStream inOIS;

	/**
	 * Special ListModel containig only unique names and associated to the users
	 * list. This user list model should be provided when creating the
	 * {@link JList} in the UI
	 */
	private NameSetListModel userListModel;

	/**
	 * List selection model of the users list indicating which elements are
	 * selected in the users list represented by the {@link #userListModel}.
	 */
	private ListSelectionModel userListSelectionModel = null;

	/**
	 * Filter used to filter messages based users names selected in the
	 * {@link #userListSelectionModel} and {@link #userListModel}
	 */
	private AuthorListFilter authorFilter = null;

	/**
	 * Flag indicating the filtering status (on/off)
	 */
	private boolean filtering;

	/**
	 * The {@link JTextField} containing the messages to send to server
	 */
	private JTextField sendField;

	/**
	 * {@link JLabel} indicating the name of the server we're connected to
	 */
	private JLabel serverLabel;

	/**
	 * Reference to the current window (useful in internal classes)
	 */
	protected final AbstractClientFrame frameRef;

	private int index = 0;
	
	/**
	 * Action to quit application
	 */
	private final Action quitAction = new QuitAction();

	/**
	 * Action to send message to server
	 */
	private final Action sendAction = new SendMessageAction();

	/**
	 * Action to clear messages in {@link AbstractClientFrame#document}
	 */
	private final Action clearMessagesAction = new ClearMessagesAction();

	/**
	 * Action to filter messages in the {@link AbstractClientFrame#document}
	 * based on {@link #userListModel}'s selected users in
	 * {@link #userListSelectionModel}.
	 */
	private final FilterMessagesAction filterAction = new FilterMessagesAction();

	/**
	 * Action to clear {@link #userListModel}'s {@link #userListSelectionModel}
	 * selection
	 */
	private final Action clearSelectionAction = new ClearListSelectionAction();

	/**
	 * Action to kick all {@link #userListModel}'s
	 * {@link #userListSelectionModel} selected users
	 */
	private final Action kickAction = new KickUserAction();
	
	/**
	 * Action to sort all messages by date
	 */
	private final Action sortByDateAction = new SortAction(MessageOrder.DATE);

	/**
	 * Action to sort all messages by author
	 */
	private final Action sortByUserAction = new SortAction(MessageOrder.AUTHOR);

	/**
	 * Action to sort all messages by content
	 */
	private final Action sortByContentAction = new SortAction(MessageOrder.CONTENT);
	
	/**
	 * The underlying list model to be associated with a {@link JList}.
	 * elements added or removed from this model will automatically be
	 * reflected in the {@link JList} associated to this model.
	 */
	private JList<String> userList;
	
	/**
	 *	the different modes of auto-complete
	 */
	private static enum Mode {
	    INSERT,
	    COMPLETION
	  };
	  
	private static final String COMMIT_ACTION = "commit";
	
	/**
	 * the list of keywords that trigger the Auto-complete action
     */
	private ArrayList<String> keywords = new ArrayList<String>(1000);
	  
	private Autocomplete autoComplete = new Autocomplete(sendField, keywords);
	
	/**
	 * Window constructor
	 * @param name user's name
	 * @param host server's name or IP address
	 * @param commonRun common run with other threads
	 * @param parentLogger parent logger
	 * @throws HeadlessException when code that is dependent on a keyboard,
	 * display, or mouse is called in an environment that does not support a
	 * keyboard, display, or mouse
	 */
	public ClientFrame2(String name,
	                    String host,
	                    Boolean commonRun,
	                    Logger parentLogger)
	    throws HeadlessException
	{
		// ------------------------------------------------------------
		// Attributes initialization
		// ------------------------------------------------------------
		super(name, host, commonRun, parentLogger);
		frameRef = this;
		clientName = name;
		userListModel = new NameSetListModel();
		userListModel.add(clientName);
		messages = new Vector<Message>();

		inOIS = null;

		filtering = false;

		// -------------------------------------------------------------
		// Window builder part
		// -------------------------------------------------------------
		
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu mnConnections = new JMenu("Connections");
		menuBar.add(mnConnections);

		JMenuItem mntmQuit = new JMenuItem(quitAction);
		mnConnections.add(mntmQuit);

		JMenu mnMessages = new JMenu("Messages");
		menuBar.add(mnMessages);

		JMenuItem mntmClear = new JMenuItem("Clear");
		mntmClear.setAction(clearMessagesAction);
		mnMessages.add(mntmClear);

		JCheckBoxMenuItem chckbxmntmFilter = new JCheckBoxMenuItem("Filter");
		chckbxmntmFilter.setAction(filterAction);
		mnMessages.add(chckbxmntmFilter);

		JMenu mnSort = new JMenu("Sort");
		mnMessages.add(mnSort);

		JCheckBoxMenuItem chckbxmntmSortByDate = new JCheckBoxMenuItem("Sort by Date");
		chckbxmntmSortByDate.setAction(sortByDateAction);
		mnSort.add(chckbxmntmSortByDate);

		JCheckBoxMenuItem chckbxmntmSortByAuthor = new JCheckBoxMenuItem("Sort by Author");
		chckbxmntmSortByAuthor.setAction(sortByUserAction);
		mnSort.add(chckbxmntmSortByAuthor);

		JCheckBoxMenuItem chckbxmntmSortByContent = new JCheckBoxMenuItem("Sort by Content");
		chckbxmntmSortByContent.setAction(sortByContentAction);
		mnSort.add(chckbxmntmSortByContent);

		JCheckBoxMenuItem checkBoxMenuItem = new JCheckBoxMenuItem("New check item");
		mnSort.add(checkBoxMenuItem);

		JMenu mnUsers = new JMenu("Users");
		menuBar.add(mnUsers);

		JMenuItem mntmClearSelection = new JMenuItem("Clear Selection");
		mntmClearSelection.setAction(clearSelectionAction);
		mnUsers.add(mntmClearSelection);
		
		JMenuItem mntmKickSelected = new JMenuItem("Kick Selected");
		mntmKickSelected.setAction(kickAction);
		mnUsers.add(mntmKickSelected);

		// -------------------------------------------------------------
		// End of Window builder part
		// -------------------------------------------------------------
		/*
		 * DONE Adds a window listener to the frame so the application can
		 * quit when window is closed
		 */
		addWindowListener(new FrameWindowListener());
		
		
		JPanel sendPanel = new JPanel();
		getContentPane().add(sendPanel, BorderLayout.SOUTH);
		sendPanel.setLayout(new BorderLayout(0, 0));
		sendField = new JTextField();
		sendField.setAction(sendAction);
		keywords.add(name);
		keywords.add("kick");
		keywords.add("bye");
		keywords.add("catchup");
		sendField.setFocusTraversalKeysEnabled(false);
		autoComplete = new Autocomplete(sendField, keywords);
		sendField.getDocument().addDocumentListener(autoComplete);
		// Maps the tab key to the commit action, which finishes the autocomplete
		// when given a suggestion
		sendField.getInputMap().put(KeyStroke.getKeyStroke("TAB"), COMMIT_ACTION);
		sendField.getActionMap().put(COMMIT_ACTION, autoComplete.new CommitAction());
		sendField.addKeyListener(new KeyAdapter()
		{
			public void keyPressed (KeyEvent e) {
				int code = e.getKeyCode();
			
				if (code == 37)
				{
					sendField.setText("");
				}
				if (code == 38 )
				{	
					if(index>0 && index <= messages_user.size())
					{
						index--;
						//System.out.println("up"+index +" "+messages_user.size());
					sendField.setText(messages_user.get(index)); 
					}
				}
				if (code == 40)
				{
					if(index>=0 && index < messages_user.size()-1)
					{
					index++;
					//System.out.println("down"+index +" "+messages_user.size());
					sendField.setText(messages_user.get(index)); 
					}
					else if(index == messages_user.size()-1) {
						index++;
						sendField.setText(""); 
					}
				}
			}
		});	
		sendPanel.add(sendField);
		sendField.setColumns(10);

		JButton sendButton = new JButton(sendAction);
		sendButton.setAction(sendAction);
		sendButton.setHideActionText(true);
		sendPanel.add(sendButton, BorderLayout.EAST);
		

		JScrollPane scrollPane = new JScrollPane();
		getContentPane().add(scrollPane, BorderLayout.CENTER);
		JTextPane textPane = new JTextPane();
		textPane.setEditable(false);
		DefaultCaret caret = (DefaultCaret) textPane.getCaret(); // <-- TODO replace null
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		scrollPane.setViewportView(textPane);
		/*
		 * TODO Setup document and documentStylee
		 * 	- Get Styled Document from textPane
		 * 	- Adds a new style to the document and stor it into documentStyle
		 * 	- Get foreground color from StyleConstants into defaultColor
		 */
		document = textPane.getStyledDocument();
		documentStyle = textPane.addStyle("New Style", null);
		defaultColor = StyleConstants.getForeground(documentStyle);

		/*
		 * TODO register all widgets associated to the filterAction
		 */
		filterAction.registerButton(sendButton);
		filterAction.registerButton(mntmClear);
		filterAction.registerButton(chckbxmntmSortByAuthor);
		filterAction.registerButton(chckbxmntmSortByDate);	
		filterAction.registerButton(chckbxmntmFilter);
		filterAction.registerButton(chckbxmntmSortByContent);
		filterAction.registerButton(mntmClearSelection);
		filterAction.registerButton(mntmKickSelected);
		/*
		 * TODO Setup List models
		 * 	- Add a new Cell Renderer to your list (a ColorTextRenderer)
		 * 	- Add userListModel to your creation of the JList
		 * 	- Get userListSelectionModel from your list
		 * 	- Add a new List Selection Listener
		 * 	(a UserListSelectionListener) to the userListSelectionModel
		 */
		
		
				ColorTextRenderer colorTextRenderer = new ColorTextRenderer();
		
				JScrollPane listScrollPane = new JScrollPane();
				listScrollPane.setPreferredSize(new Dimension(100, 4));
				getContentPane().add(listScrollPane, BorderLayout.WEST);
				userList = new JList<String>(userListModel);
				listScrollPane.setViewportView(userList);
				userList.setName("Elements");
				userList.setBorder(UIManager.getBorder("EditorPane.border"));
				JPopupMenu popupMenu = new JPopupMenu();
				addPopup(userList, popupMenu);
				
				JMenuItem pptmClearSelection = new JMenuItem("Clear Selection");
				pptmClearSelection.setAction(clearSelectionAction);
				popupMenu.add(pptmClearSelection);
				
						JMenuItem pptmKickSelected = new JMenuItem("Kick Selected");
						pptmKickSelected.setAction(kickAction);
						popupMenu.add(pptmKickSelected);
						userList.setCellRenderer(colorTextRenderer);
						
						JToolBar toolBar = new JToolBar();
						getContentPane().add(toolBar, BorderLayout.NORTH);
						
						JButton btnQuit = new JButton("quit");
						btnQuit.setHideActionText(true);
						btnQuit.setAction(quitAction);
						toolBar.add(btnQuit);
						
						Component horizontalStrut = Box.createHorizontalStrut(20);
						toolBar.add(horizontalStrut);
						
						JButton btnClear = new JButton("Clear");
						btnClear.setHideActionText(true);
						btnClear.setAction(clearMessagesAction);
						btnClear.setActionCommand("Clear Messages");
						toolBar.add(btnClear);
						
						JToggleButton tglbtnFilter = new JToggleButton("FIlter");
						tglbtnFilter.setHideActionText(true);
						tglbtnFilter.setAction(filterAction);
						toolBar.add(tglbtnFilter);
						
						JButton btnNewButton = new JButton("New button");
						btnNewButton.setHideActionText(true);
						btnNewButton.setAction(clearSelectionAction);
						btnNewButton.setActionCommand("Clear selection");
						toolBar.add(btnNewButton);
						
						JButton btnKick = new JButton("kick");
						btnKick.setHideActionText(true);
						btnKick.setAction(kickAction);
						toolBar.add(btnKick);
						
						Component horizontalGlue = Box.createHorizontalGlue();
						toolBar.add(horizontalGlue);
						
						serverLabel = new JLabel(host == null ? "" : host);
						toolBar.add(serverLabel);
						
						
		userListSelectionModel = userList.getSelectionModel(); // <-- TODO replace null
		UserListSelectionListener listener = new UserListSelectionListener();
		userListSelectionModel.addListSelectionListener(listener);
		
		
		/*
		 * TODO Create a new AuthorListFilter with userListModel and
		 * userListSelectionModel
		 */
		authorFilter = new AuthorListFilter(userListModel, userListSelectionModel); // <-- TODO replace null
	}

	/**
	 * Client frame's thread run loop: read {@link Message} object with {@link #inOIS} and
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run()
	{
		// DONE create an ObjectInputStream on the #inPipe to be able to read
		// Message objects
		try
		{
			inOIS = new ObjectInputStream(inPipe);
		}
		catch (StreamCorruptedException sce)
		{
			logger.severe("ClientFrame2: "
			        + Failure.USER_INPUT_STREAM.toString()
			        + " Output Object stream: " + "stream header is incorrect, "
			        + sce.getLocalizedMessage());
			System.exit(Failure.USER_INPUT_STREAM.toInteger());
		}
		catch (IOException ioe)
		{
			logger.severe("ClientFrame2: "
			        + Failure.USER_INPUT_STREAM.toString()
			        + " IOException, " + ioe.getLocalizedMessage());
			System.exit(Failure.USER_INPUT_STREAM.toInteger());
		}

		while(commonRun.booleanValue())
		{
			Message message = null;
			// DONE Read message from inOIS
			try
			{
				message = (Message)inOIS.readObject();
			}
			catch (ClassNotFoundException | InvalidClassException |
			       StreamCorruptedException | OptionalDataException e)
			{
				logger.severe("ClientFrame2 : error reading object"
				    + e.getLocalizedMessage());
				break;
			}
			catch (IOException e)
			{
				logger.severe("ClientFrame2 : error reading object "
				    + "IO Exception : " + e.getLocalizedMessage());
				break;
			}

			// DONE Add the current message to the #messages list
			messages.add(message);

			// DONE Update #userListModel with evt new author
			String author = message.getAuthor();
			if ((author != null) && (author.length() > 0))
			{
				userListModel.add(author);
				if (!keywords.contains(author)) {
					keywords.add(author);
					autoComplete = new Autocomplete(sendField, keywords);
				}
			}
			

			// DONE update messages
			updateMessages();
		}

		if (commonRun.booleanValue())
		{
			logger.info("ClientFrame::cleanup: changing run state at the end ... ");
			synchronized (commonRun)
			{
				commonRun = Boolean.FALSE;
			}
		}

		cleanup();
	}

	/**
	 * Cleanup: clear {@link #messages}, close {@link #inOIS} and calls
	 * super cleanup
	 * @see AbstractClientFrame#cleanup()
	 */
	@Override
	public void cleanup()
	{
		messages.clear();

		logger.info("ClientFrame2::cleanup: closing object input stream...");
		try
		{
			inOIS.close();
		}
		catch (IOException e)
		{
			logger.warning("ClientFrame2::cleanup: failed to close input stream"
			    + e.getLocalizedMessage());
		}

		super.cleanup();
	}

	/**
	 * Adds a new message at the end of {@link AbstractClientFrame#document}.
	 * The date part of the message "[yyyy/MM/dd HH:mm:ss]" should be displayed
	 * with default color whereas the "user > message" part should be displayed
	 * with user's specific color ({@link #getColorFromName(String)})
	 * @param message The message to display
	 * le message à afficher dans le
	 * {@link AbstractClientFrame#document} en modifiant au besoin le
	 * {@link AbstractClientFrame#documentStyle}
	 * @throws BadLocationException if the position to insert text is invalid
	 */
	protected void appendMessage(Message message)// throws BadLocationException
	{
		/*
		 * adds "[yyyy/MM/dd HH:mm:ss] user > message" at the end of the document
		 */
		StringBuffer sb = new StringBuffer();

		sb.append(message);
		sb.append(Vocabulary.newLine);

		// parse TEXT message for name
		String source = parseName(message.toString());
		if ((source != null) && (source.length() > 0))
		{
			/*
			 * Set color for user in document style
			 */
			StyleConstants.setForeground(documentStyle,
			                             getColorFromName(source));
		}
		try
		{
			/*
			 * TODO Adds message date to the end of the document with default
			 * style
			 */
			document.insertString(document.getLength(),message.getDate().toString(), documentStyle); // <-- TODO replace
			/*
			 * TODO If message has no author (server's message) adds the
			 * message content with default style,
			 * otherwise
			 * Adds "user > content" message part with user's color
			 * obtained from AbstractClientFrame#getColorFromName
			 * followed by a new line
			 * then re-set the default style in document Style
			 */
			// TODO ...
			if(message.getAuthor() == null)
			{
				document.insertString(document.getLength(), message.getContent(), documentStyle);		
			}
			else {
				Color color = getColorFromName(message.getAuthor());
				StyleConstants.setForeground(documentStyle,color);
				document.insertString(document.getLength(),
	                      sb.toString(),
	                      documentStyle);
				StyleConstants.setForeground(documentStyle,defaultColor);
			}
		}
		catch (BadLocationException ble)
		{
			logger.warning("ClientFrame2::appendMessage(...); Bad Location : "
			    + ble.getLocalizedMessage());
		}
	}
	
	/**
	 * Search for user's name in a string formatted as "user > message".
	 * This method is used to extract user's name from text messages
	 * @param message the message to parse
	 * @return user's name or null if there is no user's name (server's
	 * messages)
	 */
	protected String parseName(String message)
	{
		if (message.contains(">") && message.contains("]"))
		{
			int pos1 = message.indexOf(']');
			int pos2 = message.indexOf('>');
			try
			{
				return new String(message.substring(pos1 + 2, pos2 - 1));
			}
			catch (IndexOutOfBoundsException iobe)
			{
				logger.warning("ClientFrame::parseName: index out of bounds");
				return null;
			}
		}
		else
		{
			return null;
		}
	}

	/**
	 * Update all messages in document according to {@link #authorFilter}'s
	 * status and ordering set into {@link Message} class
	 */
	protected void updateMessages() // throws BadLocationException
	{
		/*
		 * TODO Clear document with remove
		 */
		try
		{
			// Clears document
			document.remove(0, document.getLength());
			document.insertString(0, "clear", documentStyle); // <-- TODO replace
		}
		catch (BadLocationException ex)
		{
			logger.warning("ClientFrame::updateMessages: bad location"
			    + ex.getLocalizedMessage());
		}

		/*
		 * TODO Then creates a stream from messages
		 */
		Stream<Message> stream = messages.stream(); // <-- TODO replace

		/*
		 * TODO If Message has any orders then sort the stream
		 */
		if (Message.orderSize() > 0)
		{
			// If there is an ordering set into Message sort the stream
			stream = stream.sorted(); // <-- TODO replace
		}

		/*
		 * TODO if filtering is on then filter the stream with authorFilter
		 */
		if (filtering)
		{
			// filter sorted stream according to #authorFilter
			stream = stream.filter(authorFilter); // <-- TODO replace
		}

		/*
		 * DONE finally append all remaining messages on the stream with
		 * appenMessage(...)
		 */
		stream.forEach((Message m) -> appendMessage(m));
	}

	// ----------------------------------------------------------------
	// App related actions
	// ----------------------------------------------------------------
	/**
	 * Action to logout from server an quit application
	 */
	private class QuitAction extends AbstractAction
	{
		/**
		 * Serial ID because enclosing class is serializable ?
		 */
		private static final long serialVersionUID = 1230763930323271538L;

		/**
		 * Constructor.
		 * Sets name, description, icons and also action's shortcut
		 */
		public QuitAction()
		{
			putValue(LARGE_ICON_KEY, new ImageIcon(ClientFrame2.class
				.getResource("/icons/disconnected-32.png")));
			putValue(SMALL_ICON, new ImageIcon(ClientFrame2.class
				.getResource("/icons/disconnected-16.png")));
			putValue(NAME, "Quit");
			putValue(SHORT_DESCRIPTION,
				"Close connection from server and quit");
		}

		/**
		 * Action performing: Clears {@link ClientFrame#serverLabel} and send
		 * {@link Vocabulary#byeCmd} to server which should terminate this frame
		 * with the {@link AbstractClientFrame#commonRun} changing to false
		 * @param e the event that triggered this action [not used]
		 */
		@Override
		public void actionPerformed(ActionEvent e)
		{
			logger.info("QuitAction: sending bye ... ");
			// TODO Complete ...
			//serverLabel.setText("");
			frameRef.validate();
			try
			{
				Thread.sleep(1000); // don't ask why
			}
			catch (InterruptedException e1)
			{
				return;
			}
			sendMessage(Vocabulary.byeCmd);
		}
	}

	// ----------------------------------------------------------------
	// Message related actions
	// ----------------------------------------------------------------
	/**
	 * Action to clear {@link AbstractClientFrame#document} content
	 */
	private class ClearMessagesAction extends AbstractAction
	{
		/**
		 * Serial ID because enclosing class is serializable ?
		 */
		private static final long serialVersionUID = -2770675891954134959L;

		/**
		 * Constructor.
		 * Sets name, description, icons and also action's shortcut
		 */
		public ClearMessagesAction()
		{
			putValue(LARGE_ICON_KEY, new ImageIcon(
				ClientFrame2.class.getResource("/icons/erase2-32.png")));
			putValue(SMALL_ICON, new ImageIcon(
				ClientFrame2.class.getResource("/icons/erase2-16.png")));
			putValue(NAME, "Clear Messages");
			putValue(SHORT_DESCRIPTION, "Clears messages in document");
		}

		/**
		 * Action performing: clears {@link AbstractClientFrame#document}
		 * content
		 * @param e the event that triggered this action [not used]
		 */
		@Override
		public void actionPerformed(ActionEvent e)
		{
			logger.info("Clear document");
			/*
			 * Clears document content
			 */
			// TODO Complete ...
			try
			{
				document.remove(0, document.getLength());
			}
			catch (BadLocationException ex)
			{
				logger.warning("ClientFrame: clear doc: bad location");
				logger.warning(ex.getLocalizedMessage());
			}
			/*
			 * Clears user's list
			 */
			// TODO Complete ...
			userListModel.clear();
			/*
			 * Clears recorded messages
			 */
			// TODO Complete ...
			messages.clear();
		}
	}

	/**
	 * Action to send message content to server
	 */
	private class SendMessageAction extends AbstractAction
	{
		/**
		 * Serial ID because enclosing class is serializable ?
		 */
		private static final long serialVersionUID = -459192941860640107L;

		/**
		 * Constructor.
		 * Sets name, description, icons and also action's shortcut
		 */
		public SendMessageAction()
		{
			putValue(SMALL_ICON, new ImageIcon(
				ClientFrame2.class.getResource("/icons/sent-16.png")));
			putValue(LARGE_ICON_KEY, new ImageIcon(
				ClientFrame2.class.getResource("/icons/sent-32.png")));
			putValue(NAME, "Send Message");
			putValue(SHORT_DESCRIPTION, "Send Message to server");
		}

		/**
		 * Action performing: retrieve {@link ClientFrame2#sendTextField}
		 * content
		 * if non empty and send it to server
		 * @param e the event that triggered this action [not used]
		 */
		@Override
		public void actionPerformed(ActionEvent e)
		{
			String content = sendField.getText(); // <-- TODO replace null
			/*
			 * TODO Send sendField content to the server with sendMessage
			 * then clears sendField content
			 */
			if (content != "") {
				if (content.length() > 0)
				{
					if (content == "catchup") {			
						sendMessage(Vocabulary.catchUpCmd);
						sendField.setText("");
					}
					else {
						messages_user.add(content);
						index = messages_user.size();
						sendMessage(content);
						sendField.setText("");
					}
				
				}
			}
		}
	}

	/**
	 * Action to filter messages according to selected users in the user's list
	 */
	private class FilterMessagesAction extends AbstractAction
	{
		/**
		 * Serial ID because enclosing class is serializable ?
		 */
		private static final long serialVersionUID = -4990621521308404832L;

		/**
		 * Collection of widgets attached to this action.
		 * We need to keep track of various widgets triggering this action since
		 * this action is a toggle, all associated widgets should be toggled at
		 * the same time whatever widget triggered this action first
		 */
		private Collection<AbstractButton> buttons;

		/**
		 * Constructor.
		 * Initialize {@link #buttons}, Sets name, description, icons and
		 * also action's shortcut
		 */
		public FilterMessagesAction()
		{
			buttons = new ArrayList<AbstractButton>();
			putValue(SMALL_ICON, new ImageIcon(ClientFrame2.class
				.getResource("/icons/filled_filter-16.png")));
			putValue(LARGE_ICON_KEY, new ImageIcon(ClientFrame2.class
				.getResource("/icons/filled_filter-32.png")));
			putValue(NAME, "Filter Messages");
			putValue(SHORT_DESCRIPTION,
				"Filter Messages according to selected users");
		}

		/**
		 * Add a new {@link AbstractButton} to the list of widgets triggering
		 * this action
		 * @param button the button to add
		 * @return true if the button was non null and not already present in
		 * the {@link #buttons} list. False otherwise.
		 */
		public boolean registerButton(AbstractButton button)
		{
			if (button != null)
			{
				if (!buttons.contains(button))
				{
					return buttons.add(button);
				}
			}
			return false;
		}

		/**
		 * Remove a button from {@link #buttons} list.
		 * @param button the button to remove
		 * @return true if the button was non null, belonged to the
		 * {@link #buttons} list and was successfully removed from
		 * {@link #buttons}
		 */
		public boolean unregisterButton(AbstractButton button)
		{
			if (button != null)
			{
				if (buttons.contains(button))
				{
					return buttons.remove(button);
				}
			}
			return false;
		}

		/**
		 * Cleanup before destruction
		 */
		@Override
		protected void finalize()
		{
			for (AbstractButton b: buttons)
			{
				unregisterButton(b);
			}

			buttons.clear();
		}

		/**
		 * Action performing: Toggle filtering on/off then
		 * {@link ClientFrame2#updateMessages()}
		 * @param e the event that triggered this action. Used to determine
		 * button source
		 */
		@Override
		public void actionPerformed(ActionEvent e)
		{
			/*
			 * TODO Get source, then source state to see if it is selected
			 * to set new filtering status
			 */
			AbstractButton button = (AbstractButton) e.getSource(); // <-- TODO replace ...
			boolean newFiltering = button.isSelected() ;  // <-- TODO replace ...
			logger.info("Filtering is " + (newFiltering ? "On" : "Off"));
			
			/*
			 * TODO Set Filtering on authorFilter and if update messages
			 * iff needed
			 */
			authorFilter.setFiltering(newFiltering);
			updateMessages();

			/*
			 * TODO Update all buttons associated to this action with
			 * new filtering status
			 */
			for(AbstractButton b:buttons) {
				b.setSelected(newFiltering);
			}
		}
	}

	// ----------------------------------------------------------------
	// User list related actions
	// ----------------------------------------------------------------
	/**
	 * Action to clear user's selection in users list
	 */
	private class ClearListSelectionAction extends AbstractAction
	{
		/**
		 * Serial ID because enclosing class is serializable ?
		 */
		private static final long serialVersionUID = 6368840308418452167L;

		/**
		 * Constructor.
		 * Sets name, description, icons and also action's shortcut
		 */
		public ClearListSelectionAction()
		{
			putValue(SMALL_ICON, new ImageIcon(ClientFrame2.class
				.getResource("/icons/delete_database-16.png")));
			putValue(LARGE_ICON_KEY, new ImageIcon(ClientFrame2.class
				.getResource("/icons/delete_database-32.png")));
			putValue(NAME, "Clear selected");
			putValue(SHORT_DESCRIPTION, "Clear selected items");
		}

		/**
		 * Action performing: Clears the
		 * {@link ClientFrame2#userListSelectionModel} and also the
		 * {@link ClientFrame2#authorFilter}
		 * @param e the event that triggered this action [not used]
		 */
		@Override
		public void actionPerformed(ActionEvent e)
		{
			/*
			 * TODO Clears selection on userListSelectionModel,
			 * authorFilter and evt update messages
			 */
			// TODO ...
			userListSelectionModel.clearSelection();
			authorFilter.clear();
			updateMessages();
		}
	}

	/**
	 * Action for kicking (or at least try to kick) all selected users from chat
	 * server
	 */
	private class KickUserAction extends AbstractAction
	{
		/**
		 * Serial ID because enclosing class is serializable ?
		 */
		private static final long serialVersionUID = -8029776262924225534L;

		/**
		 * Constructor.
		 * Sets name, description, icons and also action's shortcut
		 */
		public KickUserAction()
		{
			putValue(SMALL_ICON,
			         new ImageIcon(ClientFrame2.class
			             .getResource("/icons/remove_user-16.png")));
			putValue(LARGE_ICON_KEY,
			         new ImageIcon(ClientFrame2.class
			             .getResource("/icons/remove_user-32.png")));
			putValue(NAME, "Kick Selected Users");
			putValue(SHORT_DESCRIPTION, "Kick users selected in the user list");
		}

		/**
		 * Action performing: Sends a {@link Vocabulary#kickCmd} for each of the
		 * users selected in the users list
		 * @param e the event that triggered this action [not used]
		 */
		@Override
		public void actionPerformed(ActionEvent e)
		{
			/*
			 * TODO Get all selected user from userListSelectionModel
			 * and userListModel and send a kick request to the server
			 * for each of them.
			 * e.g. : "kick MyNemesis"
			 * N.B. Kick is part of Vocabulary : Vocabulary.kickCmd
			 */
			int minIndex = userListSelectionModel.getMinSelectionIndex(); // <-- TODO replace ...
			int maxIndex = userListSelectionModel.getMaxSelectionIndex(); // <-- TODO replace ...
			// TODO ...
			int i;
			for (i=minIndex; i<=maxIndex;i++) {
				String userKicked =  userListModel.getElementAt(i);
				sendMessage(Vocabulary.kickCmd + " " + userKicked);
			}
			sendField.setText("");
		}
	}
	
	/**
	 * Action to sort messages according to specific ordering set into
	 * {@link Message}
	 */
	private class SortAction extends AbstractAction
	{
		/**
		 * Serial ID because enclosing class is serializable ?
		 */
		private static final long serialVersionUID = -8690818752859664484L;

		/**
		 * Message ordering to set in this action:
		 * <ul>
		 * <li>{@link Message.MessageOrder#AUTHOR} to sort messages by
		 * author</li>
		 * <li>{@link Message.MessageOrder#DATE} to sort messages by date</li>
		 * <li>{@link Message.MessageOrder#CONTENT} to serot messages by
		 * content</li>
		 * </ul>
		 */
		private MessageOrder order;

		/**
		 * Constructor.
		 * Sets name, description, icons and also action's shortcut according to
		 * the desired ordering
		 * @param order the order to set for sorting messages
		 */
		public SortAction(MessageOrder order)
		{
			this.order = order;
			switch (order)
			{
				case DATE:
					putValue(LARGE_ICON_KEY,
					         new ImageIcon(ClientFrame2.class.getResource("/icons/clock-32.png")));
					putValue(SMALL_ICON,
					         new ImageIcon(ClientFrame2.class.getResource("/icons/clock-16.png")));
					break;
				case AUTHOR:
					putValue(LARGE_ICON_KEY,
					         new ImageIcon(ClientFrame2.class.getResource("/icons/gender_neutral_user-32.png")));
					putValue(SMALL_ICON,
					         new ImageIcon(ClientFrame2.class.getResource("/icons/gender_neutral_user-16.png")));
					break;
				case CONTENT:
					putValue(LARGE_ICON_KEY,
					         new ImageIcon(ClientFrame2.class.getResource("/icons/select_all-32.png")));
					putValue(SMALL_ICON,
					         new ImageIcon(ClientFrame2.class.getResource("/icons/select_all-16.png")));
					break;
				default:
					break;
			}
			putValue(NAME, order.toString());
			putValue(SHORT_DESCRIPTION, "Sort messages by " + order.toString());
		}

		/**
		 * Action performing: Set or unset this {@link #order} for sorting
		 * messages and update messages
		 * @param e the event that triggered this action. Used to determine
		 * if the widget triggering this action is selected or unseleced in
		 * order to set or unset sorting by adding or removing order into
		 * {@link Message} class.
		 */
		@Override
		public void actionPerformed(ActionEvent e)
		{
			/*
			 * TODO Get event source an cast it to get the selected
			 * state, then if selected add the corresponding order
			 * to Message with addOrder otherwise remove the
			 * corresponding order from Message with removeOrder
			 * And finally update messages
			 */
			AbstractButton button = (AbstractButton) e.getSource(); // <-- TODO replace ...
			boolean selected = button.isSelected();  // <-- TODO replace ...

			// TODO ...
			if (selected) {
				Message.addOrder(order);
			}
			else {
				Message.removeOrder(order);
			}
			updateMessages();
		}
	}

	/**
	 * Listener to handle selection changes in user's list.
	 * Updates the {@link ClientFrame2#authorFilter} according to currently
	 * selected users in the users list
	 */
	private class UserListSelectionListener implements ListSelectionListener
	{
		/**
		 * Method called when List selection changes
		 * @param ListSelectionEvent e the event that triggered this action
		 */
		@Override
		public void valueChanged(ListSelectionEvent e)
		{
			/*
			 * TODO
			 * Get first and last index of the ListSelectionEvent
			 * Get the adjusting status of the event
			 * Get the ListSelectionModel (lsm) as the source of the event
			 * Then if the event is NOT adjusting then
			 * 	Clears authorFilter
			 * 	And add each user of the userListModel selected in the
			 * lsm to the authorFilter
			 * And finally, if filtering is on updateMessages
			 *
			 * Side Note : If the list selection model is empty
			 * kickAction and clearSelectionAction should be disabled
			 * and enabled otherwise
			 */
			int firstIndex = e.getFirstIndex(); // <-- TODO replace ...
			int lastIndex = e.getLastIndex(); // <-- TODO replace ...
			boolean isAdjusting = e.getValueIsAdjusting();  // <-- TODO replace ...
			ListSelectionModel lsm = (ListSelectionModel) e.getSource(); // <-- TODO replace ...
			if (lsm.isSelectionEmpty()) {
				kickAction.setEnabled(false);
				clearSelectionAction.setEnabled(false);
			}
			else {
				kickAction.setEnabled(true);
				clearSelectionAction.setEnabled(true);
			}
			/*
			 * isAdjusting remains true while events like drag n drop are
			 * still processed and becomes false afterwards.
			 */
			if (!isAdjusting)
			{
				// TODO ...
				authorFilter.clear();
				int i;
				for (i=firstIndex; i<=lastIndex; i++) {
					if (lsm.isSelectedIndex(i)) {
						authorFilter.add(userListModel.getElementAt(i));
					}
				}
			}
			if (filtering) {
				updateMessages();
			}
		}
	}

	/**
	 * Color Text renderer for drawing list's elements in colored text
	 * @author davidroussel
	 */
	private class ColorTextRenderer extends JLabel
		implements ListCellRenderer<String>
	{
		/**
		 * Serial ID because enclosing class is serializable ?
		 */
		private static final long serialVersionUID = -3133105073504656769L;

		/**
		 * Text color
		 */
		private Color color = null;

		/**
		 * Customized rendering for a ListCell with a color obtained from
		 * the hashCode of the string to display
		 * @see
		 * javax.swing.ListCellRenderer#getListCellRendererComponent(javax.swing
		 * .JList, java.lang.Object, int, boolean, boolean)
		 */
		@Override
		public Component getListCellRendererComponent(
			JList<? extends String> list, String value, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			color = list.getForeground();
			if (value != null)
			{
				if (value.length() > 0)
				{
					color = frameRef.getColorFromName(value);
				}
			}
			setText(value);
			if (isSelected)
			{
				setBackground(color);
				setForeground(list.getSelectionForeground());
			}
			else
			{
				setBackground(list.getBackground());
				setForeground(color);
			}
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			return this;
		}
	}
	
	public class Autocomplete implements DocumentListener {


		  private JTextField textField;
		  private ArrayList<String> keywords;
		  private Mode mode = Mode.INSERT;

		  public Autocomplete(JTextField textField, ArrayList<String> keywords) {
			this.textField = textField;
		    this.keywords = keywords;
		    //this.keywords = keywords;
		    Collections.sort(keywords);
		  }

		  @Override
		  public void changedUpdate(DocumentEvent ev) { }

		  @Override
		  public void removeUpdate(DocumentEvent ev) { }

		  @Override
		  public void insertUpdate(DocumentEvent ev) {
		    if (ev.getLength() != 1)
		      return;

		    int pos = ev.getOffset();
		    String content = null;
		    try {
		      content = textField.getText(0, pos + 1);
		    } catch (BadLocationException e) {
		      e.printStackTrace();
		    }

		    // Find where the word starts
		    int w;
		    for (w = pos; w >= 0; w--) {
		      if (!Character.isLetter(content.charAt(w))) {
		        break;
		      }
		    }

		    // Too few chars
		    if (pos - w < 2)
		      return;

		    String prefix = content.substring(w + 1).toLowerCase();
		    int n = Collections.binarySearch(keywords, prefix);
		    if (n < 0 && -n <= keywords.size()) {
		      String match = keywords.get(-n - 1);
		      if (match.startsWith(prefix)) {
		        // A completion is found
		        String completion = match.substring(pos - w);
		        // We cannot modify Document from within notification,
		        // so we submit a task that does the change later
		        SwingUtilities.invokeLater(new CompletionTask(completion, pos + 1));
		      }
		    } else {
		      // Nothing found
		      mode = Mode.INSERT;
		    }
		  }

		  public class CommitAction extends AbstractAction {
		    /**
		     * 
		     */
		    private static final long serialVersionUID = 5794543109646743416L;

		    @Override
		    public void actionPerformed(ActionEvent ev) {
		      if (mode == Mode.COMPLETION) {
		        int pos = textField.getSelectionEnd();
		        StringBuffer sb = new StringBuffer(textField.getText());
		        textField.setText(sb.toString());
		        textField.setCaretPosition(pos);
		        mode = Mode.INSERT;
		      } else {
		        textField.replaceSelection("\t");
		      }
		    }
		  }

		  private class CompletionTask implements Runnable {
		    private String completion;
		    private int position;

		    CompletionTask(String completion, int position) {
		      this.completion = completion;
		      this.position = position;
		    }

		    public void run() {
		      StringBuffer sb = new StringBuffer(textField.getText());
		      sb.insert(position, completion);
		      textField.setText(sb.toString());
		      textField.setCaretPosition(position + completion.length());
		      textField.moveCaretPosition(position);
		      mode = Mode.COMPLETION;
		    }
		  }

		}
	
	

	/**
	 * Class redirecting the window closing event to the {@link QuitAction}
	 */
	protected class FrameWindowListener extends WindowAdapter
	{
		/**
		 * Method trigerred when window is closing
		 * @param e The Window event
		 */
		@Override
		public void windowClosing(WindowEvent e)
		{
			logger.info("FrameWindowListener::windowClosing: sending bye ... ");
			/*
			 * Calls the #quitAction if there is any
			 */
			if (quitAction != null)
			{
				quitAction.actionPerformed(null);
			}
		}
	}
	/**
	 * Adds a popup menu to a component
	 * @param component the parent component of the popup menu
	 * @param popup the popup menu to add
	 */
	private static void addPopup(Component component, final JPopupMenu popup)
	{
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					showMenu(e);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					showMenu(e);
				}
			}

			private void showMenu(MouseEvent e)
			{
				popup.show(e.getComponent(), e.getX(), e.getY());
			}
		});
	}

}

