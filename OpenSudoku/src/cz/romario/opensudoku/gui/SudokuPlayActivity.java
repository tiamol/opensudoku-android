package cz.romario.opensudoku.gui;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.Toast;
import cz.romario.opensudoku.R;
import cz.romario.opensudoku.db.SudokuDatabase;
import cz.romario.opensudoku.game.SudokuCell;
import cz.romario.opensudoku.game.SudokuGame;
import cz.romario.opensudoku.game.SudokuGame.OnPuzzleSolvedListener;
import cz.romario.opensudoku.gui.SudokuBoardView.OnCellTappedListener;
import cz.romario.opensudoku.gui.inputmethod.IMControlPanel;
import cz.romario.opensudoku.gui.inputmethod.IMNumpad;
import cz.romario.opensudoku.gui.inputmethod.IMPopup;
import cz.romario.opensudoku.gui.inputmethod.IMSingleNumber;

/*
 */
public class SudokuPlayActivity extends Activity{
	
	public static final int MENU_ITEM_RESTART = Menu.FIRST;
	public static final int MENU_ITEM_CLEAR_ALL_NOTES = Menu.FIRST + 1;
	public static final int MENU_ITEM_UNDO = Menu.FIRST + 2;
	public static final int MENU_ITEM_HELP = Menu.FIRST + 3; 
	public static final int MENU_ITEM_SETTINGS = Menu.FIRST + 4;
	
	
	//private static final String TAG = "SudokuPlayActivity";
	
	public static final String EXTRAS_SUDOKU_ID = "sudoku_id";
	
	private static final int DIALOG_RESTART = 1;
	private static final int DIALOG_WELL_DONE = 2;
	private static final int DIALOG_CLEAR_NOTES = 3;

	private long mSudokuGameID;
	private SudokuGame mSudokuGame;
	
	private SudokuBoardView mSudokuBoard;
	
	private IMControlPanel mInputMethods;
	private IMPopup mIMPopup;
	private IMSingleNumber mIMSingleNumber;
	private IMNumpad mIMNumpad;
	
	private StringBuilder mTimeText;
	private Formatter mTimeFormatter;
	
	private GameTimer mGameTimer;
	
	private HintsQueue mHintsQueue;
	
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
		setContentView(R.layout.sudoku_play);
    
		mHintsQueue = new HintsQueue(this);
		
        mSudokuBoard = (SudokuBoardView)findViewById(R.id.sudoku_board);
        
        mTimeText = new StringBuilder(5);
        mTimeFormatter = new Formatter(mTimeText);
        mGameTimer = new GameTimer();
        
        
        
        // create sudoku game instance
        if (savedInstanceState == null) {
        	// activity runs for the first time, read game from database
        	mSudokuGameID = getIntent().getLongExtra(EXTRAS_SUDOKU_ID, 0);
        	SudokuDatabase sudokuDB = new SudokuDatabase(this);
        	mSudokuGame = sudokuDB.getSudoku(mSudokuGameID);
        	//gameTimer.setTime(sudokuGame.getTime());
        } else {
        	// activity has been running before, restore its state
        	mSudokuGame = (SudokuGame)savedInstanceState.getParcelable("sudoku_game");
        	mGameTimer.restoreState(savedInstanceState);
        }
        
        if (mSudokuGame.getState() == SudokuGame.GAME_STATE_NOT_STARTED) {
        	mSudokuGame.start();
        } else if (mSudokuGame.getState() == SudokuGame.GAME_STATE_PLAYING) {
        	mSudokuGame.resume();
        } 
        
        if (mSudokuGame.getState() == SudokuGame.GAME_STATE_COMPLETED) {
        	mSudokuBoard.setReadOnly(true);
        }

        mSudokuBoard.setGame(mSudokuGame);
		mSudokuGame.setOnPuzzleSolvedListener(onSolvedListener);
		
		mHintsQueue.showOneTimeHint(R.string.welcome, R.string.first_run_hint);		
		
        mInputMethods = (IMControlPanel)findViewById(R.id.input_methods);
        mInputMethods.setBoard(mSudokuBoard);
        mInputMethods.setGame(mSudokuGame);
        mInputMethods.setHintsQueue(mHintsQueue);
        mIMPopup = new IMPopup();
        mInputMethods.addInputMethod(mIMPopup);
        mIMSingleNumber = new IMSingleNumber();
        mInputMethods.addInputMethod(mIMSingleNumber);
        mIMNumpad = new IMNumpad();
        mInputMethods.addInputMethod(mIMNumpad);
		updateTime();
    }
	
	@Override
	protected void onResume() {
		super.onResume();
		
		if (mSudokuGame.getState() == SudokuGame.GAME_STATE_PLAYING) {
			mSudokuGame.resume();
			mGameTimer.start();
		}
		
        // read game settings
		SharedPreferences gameSettings = PreferenceManager.getDefaultSharedPreferences(this);
        mSudokuGame.setHighlightWrongVals(gameSettings.getBoolean("highlight_wrong_values", true));
        
        mIMPopup.enabled = gameSettings.getBoolean("im_popup", true);
        mIMSingleNumber.enabled = gameSettings.getBoolean("im_single_number", true);
        mIMNumpad.enabled = gameSettings.getBoolean("im_numpad", true);

        mInputMethods.ensureSomethingIsActive();
        
        
	}
	
	
	
    @Override
    protected void onPause() {
    	super.onPause();
		
    	if (mSudokuGame.getState() == SudokuGame.GAME_STATE_PLAYING) {
			mSudokuGame.pause();
		}
    	
    	// we will save game to the database as we might not be able to get back
		SudokuDatabase sudokuDB = new SudokuDatabase(SudokuPlayActivity.this);
		sudokuDB.updateSudoku(mSudokuGame);
		
		mGameTimer.stop();
		
		mInputMethods.pause();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
		
    	mGameTimer.stop();
    	outState.putParcelable("sudoku_game", mSudokuGame);
    	mGameTimer.saveState(outState);
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
        menu.add(0, MENU_ITEM_UNDO, 0, R.string.undo)
        .setShortcut('1', 'u')
        .setIcon(android.R.drawable.ic_menu_revert);
		
        menu.add(0, MENU_ITEM_CLEAR_ALL_NOTES, 0, R.string.clear_all_notes)
        .setShortcut('3', 'a')
        .setIcon(android.R.drawable.ic_menu_delete);

        menu.add(0, MENU_ITEM_RESTART, 1, R.string.restart)
        .setShortcut('7', 'r')
        .setIcon(android.R.drawable.ic_menu_rotate);

        menu.add(0, MENU_ITEM_HELP, 1, R.string.help)
        .setShortcut('0', 'h')
        .setIcon(android.R.drawable.ic_menu_help);
        
        menu.add(0, MENU_ITEM_SETTINGS, 1, R.string.settings)
        .setShortcut('9', 's')
        .setIcon(android.R.drawable.ic_menu_preferences);
        
        

        

        // Generate any additional actions that can be performed on the
        // overall list.  In a normal install, there are no additional
        // actions found here, but this allows other applications to extend
        // our menu with their own actions.
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, FolderListActivity.class), null, intent, 0, null);

        return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
		menu.findItem(MENU_ITEM_UNDO).setEnabled(mSudokuGame.hasSomethingToUndo());
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ITEM_RESTART:
        	showDialog(DIALOG_RESTART);
            return true;
        case MENU_ITEM_CLEAR_ALL_NOTES:
        	showDialog(DIALOG_CLEAR_NOTES);
        	return true;
        case MENU_ITEM_UNDO:
        	mSudokuGame.undo();
        	mSudokuBoard.postInvalidate();
        	return true;
        case MENU_ITEM_SETTINGS:
        	Intent i = new Intent();
        	i.setClass(this, GameSettingsActivity.class);
        	startActivity(i);
        	return true;
        case MENU_ITEM_HELP:
        	mHintsQueue.showHint(R.string.help, R.string.help_text);
        	return true;
        }
        return super.onOptionsItemSelected(item);
	}
    
    @Override
    protected Dialog onCreateDialog(int id) {
    	switch (id){
//    	case DIALOG_SELECT_NUMBER:
//    		return selectNumberDialog.getDialog();
//    	case DIALOG_SELECT_MULTIPLE_NUMBERS:
//			return selectMultipleNumbersDialog.getDialog();
    	case DIALOG_WELL_DONE:
            return new AlertDialog.Builder(SudokuPlayActivity.this)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setTitle(R.string.well_done)
            .setMessage(getString(R.string.congrats, getTime()))
            .setPositiveButton(android.R.string.ok, null)
            .create();
    	case DIALOG_RESTART:
            return new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_menu_rotate)
            .setTitle(R.string.app_name)
            .setMessage(R.string.restart_confirm)
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Restart game
                	mSudokuGame.reset();
                	mSudokuGame.start();
                	mSudokuBoard.setReadOnly(false);
                	mSudokuBoard.postInvalidate();
                	mGameTimer.start();
                }
            })
            .setNegativeButton(android.R.string.no, null)
            .create();
    	case DIALOG_CLEAR_NOTES:
            return new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_menu_delete)
            .setTitle(R.string.app_name)
            .setMessage(R.string.clear_all_notes_confirm)
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                	mSudokuGame.clearAllNotes();
                	mSudokuBoard.postInvalidate();
                }
            })
            .setNegativeButton(android.R.string.no, null)
            .create();
    	}
    	return null;
    }
    
    /**
     * Occurs when puzzle is solved.
     */
    private OnPuzzleSolvedListener onSolvedListener = new OnPuzzleSolvedListener() {

		@Override
		public void onPuzzleSolved() {
			mSudokuBoard.setReadOnly(true);
			mSudokuBoard.postInvalidate();
			showDialog(DIALOG_WELL_DONE);
		}
    	
    };
    
	// TODO: can Chronometer replace this?
	/**
     * Update the time of game-play.
     */
	void updateTime() {
		setTitle(getTime());
	}
	
	public String getTime() {
		long time = mSudokuGame.getTime();
		mTimeText.setLength(0);
		mTimeFormatter.format("%02d:%02d", time / 60000, time / 1000 % 60);
		return mTimeText.toString();
	}
	
	// This class implements the game clock.  All it does is update the
    // status each tick.
	private final class GameTimer extends Timer {
		
		GameTimer() {
    		super(1000);
    	}
		
    	@Override
		protected boolean step(int count, long time) {
    		updateTime();
            
            // Run until explicitly stopped.
            return false;
        }
        
	}
}
