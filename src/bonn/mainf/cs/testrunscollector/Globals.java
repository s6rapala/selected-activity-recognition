
package bonn.mainf.cs.testrunscollector;
/**
 * @author Raghu Palakodety
 * @todo classification task
 *
 */
public abstract class Globals {

	// Debugging tag
	public static final String TAG = "MyRuns";

	// For Blocking queue, capacity set to 2048.
	public static final int ACCELEROMETER_BUFFER_CAPACITY = 2048;

	// For collecting accelerometer 64 raw data values into a a dump buffer and
	// constructing fft_coeff_
	public static final int ACCELEROMETER_BLOCK_CAPACITY = 64; //64

	// Proposed activity recognition constants for use.
	public static final int ACTIVITY_ID_STANDING = 0;
	public static final int ACTIVITY_ID_WALKING = 1;
	public static final int ACTIVITY_ID_RUNNING = 2;
	public static final int ACTIVITY_ID_OTHER = 2;

	public static final int SERVICE_TASK_TYPE_COLLECT = 0;
	public static final int SERVICE_TASK_TYPE_CLASSIFY = 1;

	public static final String ACTION_MOTION_UPDATED = "MYRUNS_MOTION_UPDATED";

	// For defining class labels in Classification stage
	public static final String CLASS_LABEL_KEY = "label";
	public static final String CLASS_LABEL_STANDING = "standing";
	public static final String CLASS_LABEL_WALKING = "walking";
	public static final String CLASS_LABEL_RUNNING = "running";
	public static final String CLASS_LABEL_OTHER = "others";

	// For constructing feature vector consisting of 64 fft co-effecients,
	// DecimalFormat appended.
	public static final String FEAT_FFT_COEF_LABEL = "fft_coef_";
	public static final String FEAT_MAX_LABEL = "max";
	public static final String FEAT_SET_NAME = "accelerometer_features";

	// For storing real-time feature vectors into a file in external SD-card
	public static final String FEATURE_FILE_NAME = "features.arff";
	public static final String TRAINING_FILE_NAME = "trainingData.arff";
	public static final String TEST_FILE_NAME = "testData.arff";
	public static final String RAW_DATA_NAME = "raw_data.txt";
	public static final String MATT_COR_COEFF = "matt_corr_coeff.txt";	

	// Instances or feature vectors collected set to a capacity of 10,000
	public static final int FEATURE_SET_CAPACITY = 10000;

	// Fur further use for displaying, but not supported by android froyo 2.2
	// until I get back my tablet
	public static final int NOTIFICATION_ID = 1;


}
