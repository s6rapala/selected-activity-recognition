package bonn.mainf.cs.testrunscollector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import com.meapsoft.FFT;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.ArffLoader;
import weka.core.converters.ConverterUtils.DataSource;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.os.Build;

public class CollectAndClassify extends Service implements SensorEventListener {
	private static final int mFeatLen = Globals.ACCELEROMETER_BLOCK_CAPACITY + 2;	
	public static final float ALPHA = 0.15f;	
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;	
	private static ArrayBlockingQueue<Double> mAccBuffer;
	private int mServiceTaskType;	
	public File mOnTheFlyData;
	public File mFeatureFile;
	public File mModelFile;
	private Attribute mClassAttribute;	
	public Instances mtestSet;	
	public Instances mDataset;
	private OnSensorChangedTask mAsyncTask;	
	public float p,q,r;
	public float[] last = {p,q,r};
	RandomForest mtreeClassifier = new RandomForest();
	public Evaluation mEval;	
	
	@Override
	public void onCreate(){
		super.onCreate();
		mAccBuffer = new ArrayBlockingQueue<Double>( Globals.ACCELEROMETER_BUFFER_CAPACITY );
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		mFeatureFile = new File(getExternalFilesDir(null), Globals.FEATURE_FILE_NAME);

		DataSource source;
		
		try {
			source = new DataSource(new FileInputStream(mFeatureFile));			
			mDataset = source.getDataSet();		
			mDataset.setClassIndex(mDataset.numAttributes() - 1);
			//mtreeClassifier.buildClassifier(mDataset);
			//mEval = new Evaluation(mDataset);
//			FileInputStream fis = new FileInputStream(); 
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream("/mnt/sdcard/Android/data/bonn.mainf.cs.testrunscollector/files/randomForest.model"));
			mtreeClassifier = (RandomForest) ois.readObject();
			ois.close();
			
			
//			ObjectOutputStream oos = new ObjectOutputStream( new FileOutputStream(new File(getExternalFilesDir(null),Globals.MODEL_FILE_NAME)));
//			oos.writeObject(mtreeClassifier);
//			oos.flush();
//			oos.close();				
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
		Bundle extras = intent.getExtras();
		mServiceTaskType = extras.getInt(Globals.COLLECT_AND_CLASSIFY);
		mOnTheFlyData = new File(getExternalFilesDir(null), Globals.ON_THE_FLY_DATA_FILE);
		Log.d(Globals.TAG, mOnTheFlyData.getAbsolutePath());
		// Create the container for attributes
		ArrayList<Attribute> allAttr = new ArrayList<Attribute>();
		// Adding FFT coefficient attributes
		DecimalFormat df = new DecimalFormat("0000");
		for (int i = 0; i < Globals.ACCELEROMETER_BLOCK_CAPACITY; i++) {
			allAttr.add(new Attribute(Globals.FEAT_FFT_COEF_LABEL + df.format(i)));
		}
		// Adding the max feature
		allAttr.add(new Attribute(Globals.FEAT_MAX_LABEL));
		ArrayList<String> labelItems = new ArrayList<String>(3);
		labelItems.add(Globals.CLASS_LABEL_STANDING);
		labelItems.add(Globals.CLASS_LABEL_WALKING);
		labelItems.add(Globals.CLASS_LABEL_RUNNING);
		labelItems.add(Globals.CLASS_LABEL_OTHER);
		mClassAttribute = new Attribute(Globals.CLASS_LABEL_KEY, labelItems);
		allAttr.add(mClassAttribute);
		mtestSet = new Instances(Globals.FEAT_SET_NAME, allAttr, Globals.FEATURE_SET_CAPACITY);
		// Set the last column/attribute (standing/walking/running) as the class
		// index for classification
		mtestSet.setClassIndex(mtestSet.numAttributes()-1);
		Intent i = new Intent(this, CollectorActivity.class);
		// Refer // http://developer.android.com/guide/topics/manifest/activity-element.html#lmode
		// IMPORTANT!. no re-create activity
		i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);
		
//		try {
//			//mModelFile = new File(getExternalFilesDir(null), Globals.MODEL_FILE_NAME);			
//			//mtreeClassifier = (RandomForest) weka.core.SerializationHelper.read(new FileInputStream(mModelFile));
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		mAsyncTask = new OnSensorChangedTask();
		mAsyncTask.execute();

		return START_NOT_STICKY;		
	}
	
	private class OnSensorChangedTask extends AsyncTask<Void, Double, Void> {

		@Override
		protected Void doInBackground(Void... arg0) {
			// TODO Auto-generated method stub
			Instance inst = new DenseInstance(mFeatLen);
			inst.setDataset(mtestSet);
			int blockSize = 0;
			FFT fft = new FFT(Globals.ACCELEROMETER_BLOCK_CAPACITY);
			
			double[] accBlock = new double[Globals.ACCELEROMETER_BLOCK_CAPACITY];
			double[] re = accBlock;
			double[] im = new double[Globals.ACCELEROMETER_BLOCK_CAPACITY];

			double max = Double.MIN_VALUE;
			while (true) {
				try {
					// need to check if the AsyncTask is cancelled or not in the while loop
					if (isCancelled () == true)
				    {
				        return null;
				    }
					
					accBlock[blockSize++] = mAccBuffer.take().doubleValue();
					if (blockSize == Globals.ACCELEROMETER_BLOCK_CAPACITY) {
						blockSize = 0;
						max = .0;
						for (double val : accBlock) {
							if (max < val) {
								max = val;
							}
						}
						fft.fft(re, im);

						for (int i = 0; i < re.length; i++) {
							double mag = Math.sqrt(re[i] * re[i] + im[i]
									* im[i]);
							inst.setValue(i, mag);
							im[i] = .0; // Clear the field
						}
						inst.setValue(Globals.ACCELEROMETER_BLOCK_CAPACITY, max);
						inst.setValue(mClassAttribute, Globals.CLASS_LABEL_STANDING);
						mtestSet.add(inst);
						Log.i("new instance", mtestSet.size() + "");
						
						if(mtestSet.numInstances() == 64){
							for(int i =0; i < mtestSet.numInstances(); i++){
								try {
									double clsLabel = mtreeClassifier.classifyInstance(mtestSet.instance(i));
									publishProgress(clsLabel);
									
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}

						}
						
					}					
				}catch(Exception ex){
					ex.printStackTrace();
				}
					
			}
//				try {
//					mEval.evaluateModel(mtreeClassifier, mtestSet);
//				} catch (Exception e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//				System.out.println(mEval.toSummaryString("\nResults\n======\n", false));
			}
		
		public void onProgressUpdate(Double... clslabelArray){
			if(clslabelArray[0] == 0.0){
				Toast.makeText(getApplicationContext(), R.string.ui_collector_toast_standing, Toast.LENGTH_SHORT).show();				
			}
			else if(clslabelArray[0] == 1){
				Toast.makeText(getApplicationContext(), R.string.ui_collector_toast_walking, Toast.LENGTH_SHORT).show();
			}
			else if(clslabelArray[0] == 2){
				Toast.makeText(getApplicationContext(), R.string.ui_collector_toast_running, Toast.LENGTH_SHORT).show();
			}
			else if(clslabelArray[0] == 3){
				Toast.makeText(getApplicationContext(), R.string.ui_collector_toast_others, Toast.LENGTH_SHORT).show();
			}			
			
						
		}
	}
		
	

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
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
	

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

}
