
package bonn.mainf.cs.testrunscollector;

import java.io.File;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import bonn.mainf.cs.testrunscollector.R;

/**
 * @author Raghu
 *
 */
public class CollectorActivity extends Activity {
	public final static String COLLECT_AND_CLASSIFY = "classify";
	private enum State {
		IDLE, COLLECTING, TRAINING, CLASSIFYING
	};

	private final String[] mLabels = { Globals.CLASS_LABEL_STANDING,
			Globals.CLASS_LABEL_WALKING, Globals.CLASS_LABEL_RUNNING,
			Globals.CLASS_LABEL_OTHER };

	private RadioGroup radioGroup;
	private final RadioButton[] radioBtns = new RadioButton[4];
	private Intent mServiceIntent;
	private File mFeatureFile;

	private State mState;
	private Button btnDelete;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		radioGroup = (RadioGroup) findViewById(R.id.radioGroupLabels);
		radioBtns[0] = (RadioButton) findViewById(R.id.radioStanding);
		radioBtns[1] = (RadioButton) findViewById(R.id.radioWalking);
		radioBtns[2] = (RadioButton) findViewById(R.id.radioRunning);
		radioBtns[3] = (RadioButton) findViewById(R.id.radioOther);

		btnDelete = (Button) findViewById(R.id.btnDeleteData);

		mState = State.IDLE;
		mFeatureFile = new File(getExternalFilesDir(null),
				Globals.FEATURE_FILE_NAME);
		mServiceIntent = new Intent(this, SensorsService.class);
	}

	public void onCollectClicked(View view) {

		if (mState == State.IDLE) {
			mState = State.COLLECTING;
			((Button) view).setText(R.string.ui_collector_button_stop_title);
			btnDelete.setEnabled(false);
			radioBtns[0].setEnabled(false);
			radioBtns[1].setEnabled(false);
			radioBtns[2].setEnabled(false);
			radioBtns[3].setEnabled(false);

			int acvitivtyId = radioGroup.indexOfChild(findViewById(radioGroup
					.getCheckedRadioButtonId()));
			String label = mLabels[acvitivtyId];

			Bundle extras = new Bundle();
			
			//Need to send class label for the service.
			extras.putString(Globals.CLASS_LABEL_KEY, label);
			mServiceIntent.putExtras(extras);

			startService(mServiceIntent);

		} else if (mState == State.COLLECTING) {
			mState = State.IDLE;
			((Button) view).setText(R.string.ui_collector_button_start_title);
			btnDelete.setEnabled(true);
			radioBtns[0].setEnabled(true);
			radioBtns[1].setEnabled(true);
			radioBtns[2].setEnabled(true);
			radioBtns[3].setEnabled(true);

			stopService(mServiceIntent);
			((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
		}
	}
	
	public void onGoClicked(View view){
		if(mState == State.IDLE){
			mState = State.CLASSIFYING;
			((Button) view).setText(R.string.ui_collector_button_stop_title);
			btnDelete.setEnabled(false);
			radioBtns[0].setEnabled(false);
			radioBtns[1].setEnabled(false);
			radioBtns[2].setEnabled(false);
			radioBtns[3].setEnabled(false);
			
			mServiceIntent = new Intent(this, CollectAndClassify.class);
			Bundle extras = new Bundle();
			extras.putInt(COLLECT_AND_CLASSIFY, Globals.SERVICE_TASK_TYPE_CLASSIFY);
			startActivity(mServiceIntent,extras);
			
			
			
			
		}
	}

	public void onDeleteDataClicked(View view) {

		if (Environment.MEDIA_MOUNTED.equals(Environment
				.getExternalStorageState())) {
			if (mFeatureFile.exists()) {
				mFeatureFile.delete();
			}

			Toast.makeText(getApplicationContext(),
					R.string.ui_collector_toast_file_deleted,
					Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onBackPressed() {

		if (mState == State.TRAINING) {
			return;
		} else if (mState == State.COLLECTING || mState == State.CLASSIFYING) {
			stopService(mServiceIntent);
			((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
					.cancel(Globals.NOTIFICATION_ID);
		}
		super.onBackPressed();
	}

	@Override
	public void onDestroy() {
		// Stop the service and the notification.
		// Need to check whether the mSensorService is null or not.
		if (mState == State.TRAINING) {
			return;
		} else if (mState == State.COLLECTING || mState == State.CLASSIFYING) {
			stopService(mServiceIntent);
			((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
					.cancel(Globals.NOTIFICATION_ID);
		}
		finish();
		super.onDestroy();
	}

}