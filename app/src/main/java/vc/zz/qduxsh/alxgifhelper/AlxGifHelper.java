package vc.zz.qduxsh.alxgifhelper;

/**
 * Created by Alex.ZHUO on 2016/8/18.
 */
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

import com.pnikosis.materialishprogress.ProgressWheel;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

/**
 * Created by Alex on 2016/6/16.
 */
public class AlxGifHelper {


    public static class ProgressViews{
        public ProgressViews(WeakReference<GifImageView> gifImageViewWeakReference, WeakReference<ProgressWheel> progressWheelWeakReference, WeakReference<TextView> textViewWeakReference,int displayWidth) {
            this.gifImageViewWeakReference = gifImageViewWeakReference;
            this.progressWheelWeakReference = progressWheelWeakReference;
            this.textViewWeakReference = textViewWeakReference;
            this.displayWidth = displayWidth;
        }

        public WeakReference<GifImageView> gifImageViewWeakReference;//gif显示控件
        public WeakReference<ProgressWheel> progressWheelWeakReference;//用来装饰的圆形进度条
        public WeakReference<TextView> textViewWeakReference;//用来显示当前进度的文本框
        public int displayWidth;//imageView的控件宽度
    }

    public static ConcurrentHashMap<String,ArrayList<ProgressViews>> memoryCache;//防止同一个gif文件建立多个下载线程,url和imageView是一对多的关系,如果一个imageView建立了一次下载，那么其他请求这个url的imageView不需要重新开启一次新的下载，这几个imageView同时回调
    //为了防止内存泄漏，这个一对多的关系均使用LRU缓存

    /**
     * 通过本地缓存或联网加载一张GIF图片
     * @param url
     * @param gifView
     */
    public static void displayImage(final String url, GifImageView gifView, ProgressWheel progressBar , TextView tvProgress, int displayWidth){
        //首先查询一下这个gif是否已被缓存
        String md5Url = getMd5(url);
        String path = gifView.getContext().getCacheDir().getAbsolutePath()+"/"+md5Url;//带.tmp后缀的是没有下载完成的，用于加载第一帧，不带tmp后缀是下载完成的，
        //这样做的目的是为了防止一个图片正在下载的时候，另一个请求相同url的imageView使用未下载完毕的文件显示一半图像
        Log.i("AlexGIF","gif图片的缓存路径是"+path);
        final File cacheFile = new File(path);
        if(cacheFile.exists()){//如果本地已经有了这个gif的缓存
            Log.i("AlexGIF","本图片有缓存");
            if(displayImage(cacheFile,gifView,displayWidth)) {//如果本地缓存读取失败就重新联网下载
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (tvProgress!=null)tvProgress.setVisibility(View.GONE);
                return;
            }
        }
        //为了防止activity被finish了但是还有很多gif还没有加载完成，导致activity没有及时被内存回收导致内存泄漏，这里使用弱引用
        final WeakReference<GifImageView> imageViewWait= new WeakReference<GifImageView>(gifView);
        final WeakReference<ProgressWheel> progressBarWait= new WeakReference<ProgressWheel>(progressBar);
        final WeakReference<TextView> textViewWait= new WeakReference<TextView>(tvProgress);
        gifView.setImageResource(R.drawable.qraved_bg_default);//设置没有下载完成前的默认图片
        if(memoryCache!=null && memoryCache.get(url)!=null){//如果以前有别的imageView加载过
            Log.i("AlexGIF","以前有别的ImageView申请加载过该gif"+url);
            //可以借用以前的下载进度，不需要新建一个下载线程了
            memoryCache.get(url).add(new ProgressViews(imageViewWait,progressBarWait,textViewWait,displayWidth));
            return;
        }
        if(memoryCache==null)memoryCache = new ConcurrentHashMap<>();
        if(memoryCache.get(url)==null)memoryCache.put(url,new ArrayList<ProgressViews>());
        //将现在申请加载的这个imageView放到缓存里，防止重复加载
        memoryCache.get(url).add(new ProgressViews(imageViewWait,progressBarWait,textViewWait,displayWidth));


        // 下载图片
        startDownLoad(url, new File(cacheFile.getAbsolutePath()+".tmp"), new DownLoadTask() {
            @Override
            public void onStart() {
                Log.i("AlexGIF","下载GIF开始");
                ProgressWheel progressBar = progressBarWait.get();
                TextView tvProgress = textViewWait.get();
                if(progressBar!=null){
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(0);
                    if(tvProgress==null)return;
                    tvProgress.setVisibility(View.VISIBLE);
                    tvProgress.setText("1%");
                }
            }

            @Override
            public void onLoading(long total, long current) {
                int progress = 0;
                //得到要下载文件的大小，是通过http报文的header的Content-Length获得的，如果获取不到就是-1
                if(total>0)progress = (int)(current*100/total);
                Log.i("AlexGIF","下载gif的进度是"+progress+"%"+"    现在大小"+current+"   总大小"+total);
                ArrayList<ProgressViews> viewses = memoryCache.get(url);
                if(viewses ==null)return;
                Log.i("AlexGIF","该gif的请求数量是"+viewses.size());
                for(ProgressViews vs : viewses){//遍历所有的进度条，修改同一个url请求的进度显示
                    ProgressWheel progressBar = vs.progressWheelWeakReference.get();
                    if(progressBar!=null){
                        progressBar.setProgress((float)progress/100f);
                        if(total==-1)progressBar.setProgress(20);//如果获取不到大小，就让进度条一直转
                    }
                    TextView tvProgress = vs.textViewWeakReference.get();
                    if(tvProgress != null)tvProgress.setText(progress+"%");
                    //显示第一帧直到全部下载完之后开始动画
                    getFirstPicOfGIF(new File(cacheFile.getAbsolutePath()+".tmp"),vs.gifImageViewWeakReference.get());
                }

            }

            public void onSuccess(File file) {
                if(file==null)return;
                String path = file.getAbsolutePath();
                if(path==null || path.length()<5)return;
                File downloadFile = new File(path);
                File renameFile = new File(path.substring(0,path.length()-4));
                if(path.endsWith(".tmp"))downloadFile.renameTo(renameFile);//将.tmp后缀去掉
                Log.i("AlexGIF","下载GIf成功,文件路径是"+path+" 重命名之后是"+renameFile.getAbsolutePath());
                if(memoryCache==null)return;
                ArrayList<ProgressViews> viewArr = memoryCache.get(url);
                if(viewArr==null || viewArr.size()==0)return;
                for(ProgressViews ws:viewArr){//遍历所有的进度条和imageView，同时修改所有请求同一个url的进度
                    //显示imageView
                    GifImageView gifImageView = ws.gifImageViewWeakReference.get();
                    if (gifImageView!=null)displayImage(renameFile,gifImageView,ws.displayWidth);
                    //修改进度条
                    TextView tvProgress = ws.textViewWeakReference.get();
                    ProgressWheel progressBar = ws.progressWheelWeakReference.get();
                    if(progressBar!=null)progressBar.setVisibility(View.GONE);
                    if(tvProgress!=null)tvProgress.setVisibility(View.GONE);
                }
                Log.i("AlexGIF",url+"的imageView已经全部加载完毕，共有"+viewArr.size()+"个");
                memoryCache.remove(url);//这个url的全部关联imageView都已经显示完毕，清除缓存记录
            }

            @Override
            public void onFailure(Throwable e) {
                Log.i("Alex","下载gif图片出现异常",e);
                TextView tvProgress = textViewWait.get();
                ProgressWheel progressBar = progressBarWait.get();
                if(progressBar!=null)progressBar.setVisibility(View.GONE);
                if(tvProgress!=null)tvProgress.setText("image download failed");
                if(memoryCache!=null)memoryCache.remove(url);//下载失败移除所有的弱引用
            }
        });
    }



    /**
     * 通过本地文件显示GIF文件
     * @param localFile 本地的文件指针
     * @param gifImageView
     * displayWidth imageView控件的宽度，用于根据gif的实际高度重设控件的高度来保证完整显示，传0表示不缩放gif的大小，显示原始尺寸
     */
    public static boolean displayImage(File localFile,GifImageView gifImageView,int displayWidth){
        if(localFile==null || gifImageView==null)return false;
        Log.i("AlexGIF","准备加载gif"+localFile.getAbsolutePath()+"显示宽度为"+displayWidth);
        GifDrawable gifFrom;
        try {
            gifFrom = new GifDrawable(localFile);
            int raw_height = gifFrom.getIntrinsicHeight();
            int raw_width = gifFrom.getIntrinsicWidth();
            Log.i("AlexGIF","图片原始height是"+raw_height+"  图片原始宽是:"+raw_width);
            if(gifImageView.getScaleType() != ImageView.ScaleType.CENTER_CROP && gifImageView.getScaleType()!= ImageView.ScaleType.FIT_XY){
                //如果大小应该自适应的话进入该方法（也就是wrap content），不然高度不会自动变化
                if(raw_width<1 || raw_height<1)return false;
                int imageViewWidth = displayWidth;
                if(imageViewWidth < 1)imageViewWidth = raw_width;//当传来的控件宽度不大对的时候，就显示gif的原始大小
                int imageViewHeight = imageViewWidth*raw_height/raw_width;
                Log.i("AlexGIF","缩放完的gif是"+imageViewWidth+" X "+imageViewHeight);
                ViewGroup.LayoutParams params = gifImageView.getLayoutParams();
                if(params!=null){
                    params.height = imageViewHeight;
                    params.width = imageViewWidth;
                }
            }else {
                Log.i("AlexGIF","按照固定大小进行显示");
            }
            gifImageView.setImageDrawable(gifFrom);
            return true;
        } catch (IOException e) {
            Log.i("AlexGIF","显示gif出现异常",e);
            return false;
        }
    }

    /**
     * 用于获取一个String的md5值
     * @param str
     * @return
     */
    public static String getMd5(String str) {
        if(str==null || str.length()<1)return "no_image.gif";
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bs = md5.digest(str.getBytes());
            StringBuilder sb = new StringBuilder(40);
            for(byte x:bs) {
                if((x & 0xff)>>4 == 0) {
                    sb.append("0").append(Integer.toHexString(x & 0xff));
                } else {
                    sb.append(Integer.toHexString(x & 0xff));
                }
            }
            if(sb.length()<24)return sb.toString();
            return sb.toString().substring(8,24);//为了提高磁盘的查找文件速度，让文件名为16位
        } catch (NoSuchAlgorithmException e) {
            Log.i("Alex","MD5加密失败");
            return "no_image.gif";
        }
    }

    public static abstract class DownLoadTask{
        abstract void onStart();
        abstract void onLoading(long total, long current);
        abstract void onSuccess(File target);
        abstract void onFailure(Throwable e);
        boolean isCanceled;
    }

    /**
     * 开启下载任务到线程池里，防止多并发线程过多
     * @param uri
     * @param targetFile
     * @param task
     */
    public static void startDownLoad(final String uri, final File targetFile, final DownLoadTask task){
        final Handler handler = new Handler();
        new AlxMultiTask<Void,Void,Void>(){//开启一个多线程池，大小为cpu数量+1

            @Override
            protected Void doInBackground(Void... params) {
                task.onStart();
                downloadToStream(uri,targetFile,task,handler);
                return null;
            }
        }.executeDependSDK();
    }


    /**
     * 通过httpconnection下载一个文件，使用普通的IO接口进行读写
     * @param uri
     * @param targetFile
     * @param task
     * @return
     */
    public static long downloadToStream(String uri, final File targetFile, final DownLoadTask task, Handler handler) {

        if (task == null || task.isCanceled) return -1;

        HttpURLConnection httpURLConnection = null;
        BufferedInputStream bis = null;
        OutputStream outputStream = null;

        long result = -1;
        long fileLen = 0;
        long currCount = 0;
        try {

            try {
                final URL url = new URL(uri);
                outputStream = new FileOutputStream(targetFile);
                httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setConnectTimeout(20000);
                httpURLConnection.setReadTimeout(10000);

                final int responseCode = httpURLConnection.getResponseCode();
                if (HttpURLConnection.HTTP_OK == responseCode) {
                    bis = new BufferedInputStream(httpURLConnection.getInputStream());
                    result = httpURLConnection.getExpiration();
                    result = result < System.currentTimeMillis() ? System.currentTimeMillis() + 40000 : result;
                    fileLen = httpURLConnection.getContentLength();//这里通过http报文的header Content-Length来获取gif的总大小，需要服务器提前把header写好
                } else {
                    Log.e("Alex","downloadToStream -> responseCode ==> " + responseCode);
                    return -1;
                }
            } catch (final Exception ex) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        task.onFailure(ex);
                    }
                });
                return -1;
            }


            if (task.isCanceled) return -1;

            byte[] buffer = new byte[4096];//每4k更新进度一次
            int len = 0;
            BufferedOutputStream out = new BufferedOutputStream(outputStream);
            while ((len = bis.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                currCount += len;
                if (task.isCanceled) return -1;
                final long finalFileLen = fileLen;
                final long finalCurrCount = currCount;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        task.onLoading(finalFileLen, finalCurrCount);
                    }
                });
            }
            out.flush();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    task.onSuccess(targetFile);
                }
            });
        } catch (Throwable e) {
            result = -1;
            task.onFailure(e);
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (final Throwable e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            task.onFailure(e);
                        }
                    });
                }
            }
        }
        return result;
    }

    /**
     * 加载gif的第一帧图像，用于下载完成前占位
     * @param gifFile
     * @param imageView
     */
    public static void getFirstPicOfGIF(File gifFile,GifImageView imageView){
        if(imageView==null)return;
        if(imageView.getTag(R.style.AppTheme) instanceof Integer)return;//之前已经显示过第一帧了，就不用再显示了
        try {
            GifDrawable gifFromFile = new GifDrawable(gifFile);
            boolean canSeekForward = gifFromFile.canSeekForward();
            if(!canSeekForward)return;
            Log.i("AlexGIF","是否能显示第一帧图片"+canSeekForward);
            //下面是一些其他有用的信息
//            int frames = gifFromFile.getNumberOfFrames();
//            Log.i("AlexGIF","已经下载完多少帧"+frames);
//            int bytecount = gifFromFile.getFrameByteCount();
//            Log.i("AlexGIF","一帧至少多少字节"+bytecount);
//            long memoryCost = gifFromFile.getAllocationByteCount();
//            Log.i("AlexGIF","内存开销是"+memoryCost);
            gifFromFile.seekToFrame(0);
            gifFromFile.pause();//静止在该帧
            imageView.setImageDrawable(gifFromFile);
            imageView.setTag(R.style.AppTheme,1);//标记该imageView已经显示过第一帧了
        } catch (IOException e) {
            Log.i("AlexGIF","获取gif信息出现异常",e);
        }
    }
}
