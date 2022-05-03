package com.example.covid19identify;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class MainActivity extends AppCompatActivity {

    //int SELECT_PHOTO = 1;
    private Uri uri;
    ImageView imageView;

    private static final int RESULTS_TO_SHOW = 3;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    private Interpreter tflite;
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();
    private List<String> labelList;
    private ByteBuffer imgData = null;
    private  float[][] labelProbArray = null;
    private  String[] topLabels = null;
    private String[] topConfidence = null;

    private int DIM_IMG_SIZE_X = 224;
    private int DIM_IMG_SIZE_Y = 224;
    private int DIM_PIXEL_SIZE = 3;

    private int[] intValues;

    private PriorityQueue<Map.Entry<String,Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> a1, Map.Entry<String, Float> a2) {
                            return (a1.getValue()).compareTo(a2.getValue());
                        }
                    });

    private ImageView selected_image;
    private Button classify_button;
    private Button choose;
    private TextView label1;
    private TextView label2;
    private TextView label3;
    private TextView Confidence1;
    private TextView Confidence2;
    private TextView Confidence3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.setTitle("肺炎X光片辨識");

        intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

        //Button Choose = findViewById(R.id.choose);
        imageView = findViewById(R.id.selected_image);

        try{
            //tflite = new Interpreter(loadModelFile(),tfliteOptions);
            tflite = new org.tensorflow.lite.Interpreter(loadModelFile(),tfliteOptions);
            labelList = loadLabelList();
        }catch(Exception e){
            e.printStackTrace();
        }

        imgData = ByteBuffer.allocateDirect(4 * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());

        labelProbArray = new float[1][labelList.size()];
        topLabels = new String[RESULTS_TO_SHOW];
        topConfidence = new String[RESULTS_TO_SHOW];

        label1 = (TextView) findViewById(R.id.label1);
        label2 = (TextView) findViewById(R.id.label2);
        label3 = (TextView) findViewById(R.id.label3);

        Confidence1 = (TextView) findViewById(R.id.Confidence1);
        Confidence2 = (TextView) findViewById(R.id.Confidence2);
        Confidence3 = (TextView) findViewById(R.id.Confidence3);

        classify_button = (Button) findViewById(R.id.classify_image);
        choose = (Button) findViewById(R.id.choose);
        selected_image = (ImageView) findViewById(R.id.selected_image);

        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                    {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                    }else {
                        bringImagePicker();
                    }
                }
                else {
                    bringImagePicker();
                }
            }
        });

        classify_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    Bitmap bitmap = ((BitmapDrawable) selected_image.getDrawable()).getBitmap();
                    Bitmap bitmapIMAGE = getResizeBitmap(bitmap, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y);
                    converBitmapToByBuffer(bitmapIMAGE);
                    tflite.run(imgData, labelProbArray);
                    ptintTopKLabels();
                }catch (Exception e){
                    Toast.makeText(MainActivity.this,"請選擇圖片",Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.intro){
            Intent intent = new Intent(this,introActivity.class);
            startActivity(intent);
            return true;
        }
        else if(id == R.id.about){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("關於");
            builder.setMessage("指導老師：林灶生\n成員：李偉聖");
            builder.setPositiveButton("確定",null);
            builder.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

private long mExitTime;
@Override
public boolean onKeyDown(int keyCode, KeyEvent event) {
    if(keyCode == KeyEvent.KEYCODE_BACK) {
        if((System.currentTimeMillis() - mExitTime) > 2000) {
            Object mHelperUtils;
            Toast.makeText(this, "再按一次退出APP", Toast.LENGTH_SHORT).show();
            mExitTime = System.currentTimeMillis();
        }
        else {
            finish();
        }
    return true;
    }
    return super.onKeyDown(keyCode, event);
}

    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        Log.d("imgdata", String.format("remaining = %d",imgData.remaining()));
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];

                imgData.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                imgData.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                imgData.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
            }
        }
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();

        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);

        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        return resizedBitmap;
    }

    private void bringImagePicker() {
        CropImage.activity()
                .setGuidelines(CropImageView.Guidelines.ON).setAspectRatio(1,1).setMinCropResultSize(  300,  300)
                .setRequestedSize( 300, 300)
                .setCropShape(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? CropImageView.CropShape.RECTANGLE : CropImageView.CropShape.OVAL)
                .start(MainActivity.this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if(resultCode == RESULT_OK) {
                uri = result.getUri();
                Log.d("getUri", "getUri is OK:");
                try{
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(),uri);
                    selected_image.setImageBitmap(bitmap);
                }catch (IOException e){
                    e.printStackTrace();
                }


            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {

                Exception error =result.getError();
                label1.setText("Error Loafing Image" + error);
            }
        }
    }

    public Bitmap getResizeBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();

        float scaleWidth = ((float) newWidth) / width;
        float scalHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scalHeight);

        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm,  0, 0, width, height, matrix, false);
        return resizedBitmap;
    }

    private void converBitmapToByBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                imgData.putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((val) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }
    }

    private void ptintTopKLabels() {
        for (int i = 0;i<labelList.size(); ++i) {
            sortedLabels.add(
                    new AbstractMap.SimpleEntry<>(labelList.get(i), labelProbArray[0][i]));
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }

        final int size = sortedLabels.size();
        Log.d("result", String.format("size = %d", size));
        for (int i= 0; i<size; ++i) {
            Map.Entry<String, Float> label= sortedLabels.poll();
            Log.d("result", String.format(label.getKey()));
            topLabels[i] = label.getKey();
            topConfidence[i] = String.format("%.0f%%",label.getValue()*100);
        }

        label1.setText("1. "+topLabels[2]);
        label2.setText("2. "+topLabels[1]);
        label3.setText("3. "+topLabels[0]);
        Confidence1.setText(topConfidence[2]);
        Confidence2.setText(topConfidence[1]);
        Confidence3.setText(topConfidence[0]);
    }

    private MappedByteBuffer loadModelFile() throws IOException,IllegalArgumentException {
        //AssetFileDescriptor fileDescriptor = this.getAssets().openFd("MobileNet.tflite");
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("modelX.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabelList() throws IOException {
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader =
                //new BufferedReader(new InputStreamReader(this.getAssets().open("Lung_Label.txt")));
                new BufferedReader(new InputStreamReader(this.getAssets().open("covid19label.txt")));
        String line;
        while((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }
}