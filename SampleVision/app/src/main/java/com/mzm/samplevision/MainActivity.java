package com.mzm.samplevision;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisResult;
import com.microsoft.projectoxford.vision.contract.Caption;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * @author Margaret Mayanrd-Reid, July 2016
 *         <p>
 *         This sample app demos Microsoft's Vision API for describing an image
 *         <p>
 *         1. Include Vision library in app module build.gradle
 *         2. Include key in strings.xml
 *         3. Capture an image by camera or select from gallery
 *         4. Send image stream for vision API processing
 *         5. Get back a result including a description of the image + other image info
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    // Image
    private static final int CAMERA = 0;
    private static final int GALLERY = 1;
    private static final int REQUEST_CAPTURE_IMAGE = 1000;
    private static final int REQUEST_SELECT_IMAGE = 2000;
    private String mImageFilePath;          // file path of the image
    private Uri mImageByCameraUri;          // Uri of the image taken by camera
    private Bitmap mBitmap;                 // The bitmap image for analysis

    // UI
    @BindView(R.id.buttonAddImage)
    Button mAddImageButton;
    @BindView(R.id.image)
    ImageView mImageView;
    @BindView(R.id.analysisStatus)
    TextView mAnalysisText;
    @BindView(R.id.imageMetadata)
    TextView mImageMetadata;
    @BindView(R.id.caption)
    TextView mCaption;
    @BindView(R.id.confidence)
    TextView mConfidence;
    @BindView(R.id.tags)
    TextView mTags;

    // API
    private VisionServiceClient mClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        if (mClient == null) {
            mClient = new VisionServiceRestClient(getString(R.string.subscription_key));
        }

    }

    @OnClick(R.id.buttonAddImage)
    public void onAddImageButtonClick(View view) {

        if (view.getId() == R.id.buttonAddImage) {
            showImageChoice();
        }

    }

    /**
     * Show dialog with two choices: "Take photo" or "Choose from gallery"
     */
    private void showImageChoice() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        CharSequence camera = getResources().getString(R.string.action_photo_camera);
        CharSequence gallery = getResources().getString(R.string.action_photo_gallery);
        builder.setCancelable(true).
                setItems(new CharSequence[]{camera, gallery},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (i == CAMERA) {
                                    captureImage();
                                } else if (i == GALLERY) {
                                    selectImage();
                                }
                            }
                        });
        builder.show();
    }


    /**
     * Capture an image by launching a camera app on device
     */
    private void captureImage() {

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File photoFile = new File(dir, "sampleimage.jpg");
        mImageByCameraUri = Uri.fromFile(photoFile);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageByCameraUri);
        startActivityForResult(intent, REQUEST_CAPTURE_IMAGE);
    }


    /**
     * Select an image from gallery (by launching all apps that displays images from MediaStore)
     */
    private void selectImage() {
        Intent i = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        startActivityForResult(i, REQUEST_SELECT_IMAGE);

    }

    @Override
    protected void onActivityResult(int requestCode,    // request code
                                    int resultCode,     // result code
                                    Intent intent) {    // intent returned from activity
        super.onActivityResult(requestCode, resultCode, intent);

        switch (requestCode) {
            case REQUEST_CAPTURE_IMAGE:
                if (resultCode == RESULT_OK) {
                    mImageFilePath = mImageByCameraUri.getPath();
                } else {
                    Toast.makeText(this, "Didn't take photo with camera", Toast.LENGTH_LONG).show();
                }
                break;
            case REQUEST_SELECT_IMAGE:
                if (resultCode == RESULT_OK) {
                    // Get Uri of the selected image
                    Uri selectedImageUri = intent.getData();
                    // Get the projection - column to query from provider
                    String[] projection = {MediaStore.Images.Media.DATA};
                    // Get the cursor
                    Cursor cursor = getContentResolver().query(
                            selectedImageUri,
                            projection,
                            null,
                            null,
                            null);
                    if (cursor != null) {
                        cursor.moveToFirst();
                        // Get the image file path
                        mImageFilePath = cursor.getString(cursor.getColumnIndex(projection[0]));
                        // Remember to close the cursor
                        cursor.close();
                    }
                } else {
                    Toast.makeText(this, "No image selected", Toast.LENGTH_LONG).show();
                }
                break;
        }

        // Display image in UI and analyze image
        if (mImageFilePath != null) {
            Log.i(TAG, "path is " + mImageFilePath);
            Picasso.with(getApplicationContext())
                    .load(new File(mImageFilePath).getAbsoluteFile())
                    .resize(200, 200)
                    .centerCrop()
                    .into(new Target() {
                        @Override
                        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                            mImageView.setImageBitmap(bitmap);
                            mBitmap = bitmap;
                            analyze();
                        }

                        @Override
                        public void onBitmapFailed(Drawable errorDrawable) {
                            Toast.makeText(MainActivity.this, "Failed to load image", Toast.LENGTH_SHORT).show();

                        }

                        @Override
                        public void onPrepareLoad(Drawable placeHolderDrawable) {

                        }
                    });

        }
    }

    public void analyze() {
        mAddImageButton.setEnabled(false);
        mAnalysisText.setText("Analyzing...");

        try {
            new analyzeTask().execute();
        } catch (Exception e) {
            mAnalysisText.setText("Error encountered. Exception is: " + e.toString());
        }
    }


    /**
     * AsyncTask for analyzing the image
     */
    private class analyzeTask extends AsyncTask<Void, Void, AnalysisResult> {
        // Store error message
        private Exception e = null;

        private analyzeTask() {
        }

        @Override
        protected AnalysisResult doInBackground(Void... args) {
            try {
                return processImage();
            } catch (Exception e) {
                this.e = e;
            }

            return null;
        }

        @Override
        protected void onPostExecute(AnalysisResult result) {
            super.onPostExecute(result);

            mAnalysisText.setText("");
            if (e != null) {
                mAnalysisText.setText("Error: " + e.getMessage());
                this.e = null;
            } else {
                mImageMetadata.setText(result.metadata.width + "x" + result.metadata.height + " " + result.metadata.format);

                Caption caption = result.description.captions.get(0); // get just the first caption
                mCaption.setText(caption.text);
                mConfidence.setText(String.valueOf(caption.confidence));

                String tags = TextUtils.join(", ", result.description.tags);
                mTags.setText(tags);
            }

            mAddImageButton.setEnabled(true);

        }
    }


    /**
     * Process the image with Vision ML service
     *
     * @return
     * @throws VisionServiceException
     * @throws IOException
     */
    private AnalysisResult processImage() throws VisionServiceException, IOException {
        Gson gson = new Gson();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        // Get the analysis result from Vision ML service
        AnalysisResult result = this.mClient.describe(inputStream, 1);

        String resultString = gson.toJson(result);
        Log.i(TAG, resultString);

        return result;
    }
}
