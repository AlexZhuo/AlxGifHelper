package vc.zz.qduxsh.alxgifhelper;

import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.TextView;

import com.pnikosis.materialishprogress.ProgressWheel;

import pl.droidsonroids.gif.GifImageView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        int screenWidth = getScreenWidth(this);
        AlxGifHelper gifHelper = new AlxGifHelper();
        View gifGroup1 = findViewById(R.id.gif_group_1);
        View gifGroup2 = findViewById(R.id.gif_group_2);

        AlxGifHelper.displayImage("https://qraved-staging.s3.amazonaws.com/images/journal/data/2016/08/15/54113f968e243.gif",
                (GifImageView) gifGroup1.findViewById(R.id.gif_photo_view),
                (ProgressWheel) gifGroup1.findViewById(R.id.progress_wheel),
                (TextView) gifGroup1.findViewById(R.id.tv_progress),
                screenWidth-100//gif显示的宽度为屏幕的宽度减去边距
                );
        AlxGifHelper.displayImage("https://staging.qraved.com/journal/wp-content/uploads/2016/08/1_1466154479.gif",
                (GifImageView) gifGroup2.findViewById(R.id.gif_photo_view),
                (ProgressWheel) gifGroup2.findViewById(R.id.progress_wheel),
                (TextView) gifGroup2.findViewById(R.id.tv_progress),
                screenWidth-100//gif显示的宽度为屏幕的宽度减去边距
                );


    }

    public int getScreenWidth(Activity activity) {
        DisplayMetrics metric = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metric);
        return metric.widthPixels;
    }
}
