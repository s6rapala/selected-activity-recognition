package bonn.mainf.cs.testrunscollector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffSaver;
import weka.core.converters.ConverterUtils.DataSource;
import android.annotation.TargetApi;
import android.app.Notification;
import android.support.v4.app.NotificationCompat;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.meapsoft.FFT;

import bonn.mainf.cs.testrunscollector.R;



/**
 * @author Raghu
 *
 */
public class SensorsService extends Service implements SensorEventListener {

	private static final int mFeatLen = Globals.ACCELEROMETER_BLOCK_CAPACITY + 2;
	public static final float ALPHA = 0.15f;
	private File mFeatureFile;
	public File mTrainingDataFile;
	public File mTestDataFile;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private int mServiceTaskType;
	private String mLabel;
	private Instances mDataset;
	public Instances mtrainingSet;
	public Instances mtestSet;	
	private Attribute mClassAttribute;
	private OnSensorChangedTask mAsyncTask;
	public float p,q,r;
	public float[] last = {p,q,r};
	public Classifier cModel = (Classifier) new NaiveBayes();

	/*
	 * mAccBuffer, this is a classic "bounded buffer", in 
	 * which a fixed-sized array holds elements 
	 * inserted by producers and extracted by consumers.
	 * Below operation take() is called by this consumer running in 
	 * background thread.

	
	 * Once created, the capacity cannot be changed. 
	 * Attempts to put an element into a full queue will result 
	 * in the operation blocking; attempts to take an element 
	 * from an empty queue will similarly block.
	 */
	private static ArrayBlockingQueue<Double> mAccBuffer;
	public static final DecimalFormat mdf = new DecimalFormat("#.##");

	@Override
	public void onCreate() {
		super.onCreate();

		mAccBuffer = new ArrayBlockingQueue<Double>(
				Globals.ACCELEROMETER_BUFFER_CAPACITY);
	}


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
/* from developer documentation:
 * The data delay (or sampling rate) 
 * controls the interval at which sensor events are sent to 
 * your application via the onSensorChanged() callback method. 
 * The default data delay is suitable for monitoring typical screen orientation changes and uses a 
 * delay of 200,000 microseconds. You can specify other data delays, 
 * such as SENSOR_DELAY_GAME (20,000 microsecond delay), SENSOR_DELAY_UI (60,000 microsecond delay), or SENSOR_DELAY_FASTEST (0 microsecond delay).
 *  As of Android 3.0 (API Level 11) you can also specify the delay as an absolute value 
 *  (in microseconds).
 *  
 *  SENSOR_DELAY_FASTEST is chosen and 64 block size(ACCELEROMETER_BLOCK_CAPACITY) samples 
 *  are collected at frequency 15.852 samples per second into 
 *  the arrayblocking buffer mAccBuffer. mAccBuffer has a size of 2048.
 *  Then 64 point FFT and autocorrelation. 
 */
		mSensorManager.registerListener(this, mAccelerometer,SensorManager.SENSOR_DELAY_FASTEST);

		Bundle extras = intent.getExtras();
		
		mLabel = extras.getString(Globals.CLASS_LABEL_KEY);

		mFeatureFile = new File(getExternalFilesDir(null), Globals.FEATURE_FILE_NAME);
		
		mTrainingDataFile = new File(getExternalFilesDir(null), Globals.TRAINING_FILE_NAME);
		
		mTestDataFile = new File(getExternalFilesDir(null), Globals.TEST_FILE_NAME);
		
		Log.d(Globals.TAG, mFeatureFile.getAbsolutePath());

		mServiceTaskType = Globals.SERVICE_TASK_TYPE_COLLECT;

		// Create the container for attributes
		ArrayList<Attribute> allAttr = new ArrayList<Attribute>();

		// Adding FFT coefficient attributes
		DecimalFormat df = new DecimalFormat("0000");

		for (int i = 0; i < Globals.ACCELEROMETER_BLOCK_CAPACITY; i++) {
			allAttr.add(new Attribute(Globals.FEAT_FFT_COEF_LABEL + df.format(i)));
		}
		// Adding the max feature
		allAttr.add(new Attribute(Globals.FEAT_MAX_LABEL));

		// Declaring a nominal attribute along with its candidate values
		ArrayList<String> labelItems = new ArrayList<String>(3);
		labelItems.add(Globals.CLASS_LABEL_STANDING);
		labelItems.add(Globals.CLASS_LABEL_WALKING);
		labelItems.add(Globals.CLASS_LABEL_RUNNING);
		labelItems.add(Globals.CLASS_LABEL_OTHER);
		mClassAttribute = new Attribute(Globals.CLASS_LABEL_KEY, labelItems);
		allAttr.add(mClassAttribute);

		// Construct the dataset with the attributes specified as allAttr and
		// capacity 10000
		mDataset = new Instances(Globals.FEAT_SET_NAME, allAttr, Globals.FEATURE_SET_CAPACITY);
		mtrainingSet = new Instances(Globals.FEAT_SET_NAME,allAttr,Globals.FEATURE_SET_CAPACITY);
		mtestSet = new Instances(Globals.FEAT_SET_NAME, allAttr, Globals.FEATURE_SET_CAPACITY);

		// Set the last column/attribute (standing/walking/running) as the class
		// index for classification
		mDataset.setClassIndex(mDataset.numAttributes() - 1);
		mtrainingSet.setClassIndex(mtrainingSet.numAttributes() - 1);
		mtestSet.setClassIndex(mtestSet.numAttributes() - 1);

		Intent i = new Intent(this, CollectorActivity.class);
		// Refer // http://developer.android.com/guide/topics/manifest/activity-element.html#lmode
		// IMPORTANT!. no re-create activity
		i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

		PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

//		Notification notification = new Notification.Builder(this)
//				.setContentTitle(
//						getApplicationContext().getString(
//								R.string.ui_sensor_service_notification_title))
//				.setContentText(
//						getResources()
//								.getString(
//										R.string.ui_sensor_service_notification_content))
//				.setSmallIcon(R.drawable.greend).setContentIntent(pi).build();
//		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//		notification.flags = notification.flags
//				| Notification.FLAG_ONGOING_EVENT;
//		notificationManager.notify(0, notification);


		mAsyncTask = new OnSensorChangedTask();
		mAsyncTask.execute();

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		mAsyncTask.cancel(true);
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		mSensorManager.unregisterListener(this);
		Log.i("","");
		super.onDestroy();

	}

	private class OnSensorChangedTask extends AsyncTask<Void, Void, Void> {
		/*
		 * Created a class for executing doInBackground(). This function is called
		 * when classInstanc.execute() is performed.
		 * An asynchronous task is defined by a computation 
		 * that runs on a background thread and whose result is published
		 * on the UI thread. An asynchronous task is defined by 3 generic types, 
		 * called Params, Progress and Result, and 4 steps, 
		 * called onPreExecute, doInBackground, onProgressUpdate and onPostExecute.
		 * doInBackground() is used.
		 * 
		 */
		@Override
		protected Void doInBackground(Void... arg0) {

			Instance inst = new DenseInstance(mFeatLen);
			inst.setDataset(mDataset);
			int blockSize = 0;
			FFT fft = new FFT(Globals.ACCELEROMETER_BLOCK_CAPACITY);
			double[] accBlock = new double[Globals.ACCELEROMETER_BLOCK_CAPACITY];
			double[] re = accBlock;
			double[] im = new double[Globals.ACCELEROMETER_BLOCK_CAPACITY];

			double max = Double.MIN_VALUE;
			
			//Added for SMA
//			SMA smaInstance = new SMA(5);
//			ArrayList<Double> smaBuffer = new ArrayList<Double>();

			while (true) {
				try {
					// need to check if the AsyncTask is cancelled or not in the while loop
					if (isCancelled () == true)
				    {
				        return null;
				    }
					
					// Dumping buffer
					accBlock[blockSize++] = mAccBuffer.take().doubleValue();
					/*
					 * When blockSize reaches its full capacity of 64
					 * acceleration magnitudes of 64 samples then blockSize is
					 * reset. Largest magnitude of acceleration is found for
					 * those 64 samples and set to 'max'.
					 */
					if (blockSize == Globals.ACCELEROMETER_BLOCK_CAPACITY) {
						blockSize = 0;
						/*
						 * Logic for 5- point algorithm
						 */
//						for(int i = 0; i < 64; i++){
//							smaInstance.values.add(accBlock[i]);							
//						}
//
//						smaBuffer = smaInstance.computeValue();
//						int smaSize = smaBuffer.size();
						
						
						max = .0;
						for (double val : accBlock) {
							if (max < val) {
								max = val;
							}
						}
						/*
						 * FFT and then autocorrelation. Please refer stackflow answer.
						 * http://stackoverflow.com/questions/8006466/detecting-periodic-data-from-the-phones-accelerometer
						 * Autocorrelation: The simplest way to detect periodicity of data is autocorrelation
						 */
						fft.fft(re, im);

						for (int i = 0; i < re.length; i++) {
							double mag = Math.sqrt(re[i] * re[i] + im[i]
									* im[i]);
							inst.setValue(i, mag);
							im[i] = .0; // Clear the field
						}

						// Append max after frequency component
						inst.setValue(Globals.ACCELEROMETER_BLOCK_CAPACITY, max);
						inst.setValue(mClassAttribute, mLabel);
						mDataset.add(inst);
						Log.i("new instance", mDataset.size() + "");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		protected void onCancelled() {
			
			Log.e("123", mDataset.size()+"");
			
			if (mServiceTaskType == Globals.SERVICE_TASK_TYPE_CLASSIFY) {
				super.onCancelled();
				return;
			}
			Log.i("in the loop","still in the loop cancelled");
			String toastDisp;

			if (mFeatureFile.exists()) {

				// merge existing and delete the old dataset
				DataSource source;
				try {
					// Create a datasource from mFeatureFile where
					// mFeatureFile = new File(getExternalFilesDir(null),
					// "features.arff");
					source = new DataSource(new FileInputStream(mFeatureFile));
					// Read the dataset set out of this datasource
					Instances oldDataset = source.getDataSet();
					oldDataset.setClassIndex(mDataset.numAttributes() - 1);
					// Sanity checking if the dataset format matches.
					if (!oldDataset.equalHeaders(mDataset)) {
						// Log.d(Globals.TAG,
						// oldDataset.equalHeadersMsg(mDataset));
						throw new Exception(
								"The two datasets have different headers:\n");
					}

					// Move all items over manually
					for (int i = 0; i < mDataset.size(); i++) {
						oldDataset.add(mDataset.get(i));
					}

					mDataset = oldDataset;
					// Delete the existing old file.
					mFeatureFile.delete();
					Log.i("delete","delete the file");
				} catch (Exception e) {
					e.printStackTrace();
				}
				toastDisp = getString(R.string.ui_sensor_service_toast_success_file_updated);

			} else {
				toastDisp = getString(R.string.ui_sensor_service_toast_success_file_created)   ;
			}
			toastDisp = getString(R.string.ui_sensor_service_toast_success_file_created)   ;			
			Log.i("save","create saver here");
			// create new Arff file
			ArffSaver saver = new ArffSaver();
			// Set the data source of the file content
			saver.setInstances(mDataset);
			Log.e("1234", mDataset.size()+"");
			try {
				// Set the destination of the file.
				// mFeatureFile = new File(getExternalFilesDir(null),
				// "features.arff");
				saver.setFile(mFeatureFile);
				// Write into the file
				saver.writeBatch();
				Log.i("batch","write batch here");
				Toast.makeText(getApplicationContext(), toastDisp,
						Toast.LENGTH_SHORT).show();
			} catch (IOException e) {
				toastDisp = getString(R.string.ui_sensor_service_toast_error_file_saving_failed);
				e.printStackTrace();
			}
			
			boolean check = BuildTrainingAndTestData(mDataset);
		//	boolean TestandTrainDataFileSaveCheck = TrainDataFileSave(); 
		//	boolean TestDataFileSaveCheck = TestDataFileSave();
		//	boolean training = TrainClassifier();
		//	boolean testResults = TestClassifier();
			Log.i("toast","toast here");
			super.onCancelled();
		}

		public boolean TestClassifier() {
			// TODO Auto-generated method stub
			try {
				Evaluation eTest = new Evaluation(mtrainingSet);
				
				eTest.evaluateModel(cModel,mtestSet);
				
				double correctlyClassifiedInstances = eTest.correct();
				
				String strSummary = eTest.toSummaryString();							

				
				System.out.println(strSummary);
				
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return true;
		}

		public boolean TrainClassifier() {
			// TODO Auto-generated method stub
			try {
				cModel.buildClassifier(mtrainingSet);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
		}

		public boolean TestDataFileSave() {
			// TODO Auto-generated method stub
			ArffSaver saver = new ArffSaver();			
			saver = new ArffSaver();
			try{
					saver.setInstances(mtestSet);
					saver.setFile(mTestDataFile);
					saver.writeBatch();
				}
				
				catch(IOException ex){
					ex.printStackTrace();
				}
			
			return true;
		}

		public boolean TrainDataFileSave() {
			// TODO Auto-generated method stub
			ArffSaver saver = new ArffSaver();
			saver.setInstances(mtrainingSet);
			try{
				saver.setFile(mTrainingDataFile);
				saver.writeBatch();

			}
			catch(IOException ex){
				ex.printStackTrace();
			}
		
			return true;
		}

		public boolean BuildTrainingAndTestData(Instances dataSet) {
//			ArffLoader loader = new ArffLoader();
//			try {
//				
//				loader.setFile(new File(getExternalFilesDir(null), Globals.FEATURE_FILE_NAME));
//				Instances dataset = loader.getDataSet();
//				Instances shuffledDataSet = shuffleMainData(dataset);
//				double count = shuffledDataSet.size();
//				
//				int index = 0;
//				
//				while ( index < ( ( 2 * count ) /3 ) ){
//					mtrainingSet.add(shuffledDataSet.get(index));
//					index++;	
//				}
//				
//				while( index < count ){
//					mtestSet.add(shuffledDataSet.get(index));
//					index++;
//				}
//				
//			}
//			catch(Exception ex){
//				ex.printStackTrace();
//				return false;
//			}
//			return true;
			
			try {
				Evaluation eval = new Evaluation(dataSet);
				eval.crossValidateModel(cModel, dataSet, 10, new Random(1) );
				eval.fMeasure(dataSet.classIndex());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
	}

		public Instances shuffleMainData(Instances dataset) {
			// TODO Auto-generated method stub
			Collections.shuffle(dataset);
			return dataset;
		}

	}

	public void onSensorChanged(SensorEvent event) {

		if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {	
			
			float x, y, z;
			x = event.values[0];
			y = event.values[1];
			z = event.values[2];
			
			float [] first = {x,y,z};
			last = lowpassFilter(first,last);


			double m = Math.sqrt(last[0] * last[0] + last[1] * last[1] + last[2] * last[2]);

			// Inserts the specified element into this queue if it is possible
			// to do so immediately without violating capacity restrictions,
			// returning true upon success and throwing an IllegalStateException
			// if no space is currently available. When using a
			// capacity-restricted queue, it is generally preferable to use
			// offer.

			try {
				mAccBuffer.add(new Double(m));

			} catch (IllegalStateException e) {

				// Exception happens when reach the capacity.
				// Doubling the buffer. ListBlockingQueue has no such issue,
				// But generally has worse performance
				ArrayBlockingQueue<Double> newBuf = new ArrayBlockingQueue<Double>( mAccBuffer.size() * 2);
				mAccBuffer.drainTo(newBuf);
				mAccBuffer = newBuf;
				mAccBuffer.add(new Double(m));
			}
		}
	}

	public float[] lowpassFilter(float[] input, float[] output) {
		// TODO Auto-generated method stub
		if(output == null) 	return input;
		for(int i = 0; i < input.length; i++){
			output[i] = output[i] + ALPHA * (input[i] - output[i]);
		}
		return output;
		
	}


	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
