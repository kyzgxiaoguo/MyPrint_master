package com.zzg.myprint_master;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.print.PrintHelper;

import android.Manifest;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintJob;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

//import com.aspose.words.Document;
//import com.aspose.words.SaveFormat;
import com.tbruyelle.rxpermissions2.Permission;
import com.tbruyelle.rxpermissions2.RxPermissions;
import com.zzg.myprint_master.databinding.ActivityMainBinding;

import net.lemonsoft.lemonbubble.LemonBubble;
import net.lemonsoft.lemonbubble.LemonBubbleInfo;
import net.lemonsoft.lemonbubble.LemonBubbleView;
import net.lemonsoft.lemonbubble.interfaces.LemonBubbleMaskOnTouchContext;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.reactivex.functions.Consumer;

public class MainActivity extends AppCompatActivity {
    private Context context;
    private ActivityMainBinding binding;

    final int DOWN_ERROR = 1;
    final int DOWN_SUCCESS = 2;
    final int PRINT_START = 3;
    final int PRINT_FINISH = 5;
    final int PRINT_COMPLETED = 6;
    final int PRINT_CANCEL = 7;

    final int FILE_SET = 8;
    private PrintJob printJob;
    private int con = 0;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding= DataBindingUtil.setContentView(this,R.layout.activity_main);
        context=MainActivity.this;
        openPermissions();
        myClick();
        handler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case PRINT_START:
                        LemonBubble.showRight(MainActivity.this, "准备打印，请稍后...", 1000);
                        break;
                    case PRINT_FINISH:
//                    LemonBubble.showRoundProgress(MainActivity.this, "正在打印，请稍后...");
                        final LemonBubbleInfo lemonBubbleInfo = LemonBubble.getRoundProgressBubbleInfo();
                        lemonBubbleInfo.setTitle("正在打印，请稍后...");
                        lemonBubbleInfo.setOnMaskTouchContext(new LemonBubbleMaskOnTouchContext() {
                            @Override
                            public void onTouch(LemonBubbleInfo lemonBubbleInfo, LemonBubbleView lemonBubbleView) {
                                LemonBubble.hide();
                            }
                        });
                        lemonBubbleInfo.show(MainActivity.this);
                        break;
                    case PRINT_COMPLETED:
                        LemonBubble.showRight(MainActivity.this, "打印完成", 2000);
                        break;
                    case PRINT_CANCEL:
                        LemonBubble.showError(MainActivity.this, "打印已取消", 2000);
                        break;
                    case FILE_SET:
                        LemonBubble.showRight(MainActivity.this, "程序已将打印作业发送至打印机，请稍后...", 2000);
                        break;
                }
            }
        };
    }
    private void myClick(){
        binding.btPrintPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doPhotoPrint();
            }
        });
        binding.btPrintUrlHTML.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doHTMLPrint(0);
            }
        });
        binding.btPrintHTML.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doHTMLPrint(1);
            }
        });
        binding.btPrintContainImgHTML.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doHTMLPrint(2);
            }
        });
        binding.btPrintCustom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String wordFilePath=Environment.getExternalStorageDirectory()+"/测试打印文件.docx";
                String pdfFilePath=Environment.getExternalStorageDirectory()+"/测试打印文件.pdf";
                doPrint(pdfFilePath);
            }
        });

    }

    /**
     * 打印图片
     */
    private void doPhotoPrint(){
        PrintHelper printHelper=new PrintHelper(context);
        //此属性会自动调整图像的大小，可以更好的把要打印的图像调整到合适的打印区域
        printHelper.setScaleMode(PrintHelper.SCALE_MODE_FIT);
        //此属性会自动等比例调整图像大小，使图像充满整个打印区域，即让图像充满整个纸张
        //缺点是，打印图像的（上下左右边缘会有一部分打印不出来）
        // printHelper.setScaleMode(PrintHelper.SCALE_MODE_FILL);
        Bitmap bitmap= BitmapFactory.decodeResource(getResources(),R.drawable.scan);
        printHelper.printBitmap("TestPrint",bitmap);

    }

    /**
     * HTML打印
     */
    private void doHTMLPrint(int printType){
        WebView webView = new WebView(context);
        webView.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView webView1,String url){
                return false;
            }
            //调用打印任务的入口
            //注意，调用打印方法时，一定要先让页面加载完成，否则会出现打印不完整或空白。
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d("执行",url);
                createWebPrintJob(view);
            }
        });
        // 创建要加载的代码
        //baseUrl:网页地址，data:请求的某段代码，mimeType:加载网页的类型，encode：编码格式，historyUrl：可用历史记录
        //在线打印
//        String htmlUrl="http://43.248.49.204:8080/2020/03/31/MjAwMzMxNjczNzQzNTUw.html";
        String htmlUrl="https://developer.huawei.com/consumer/cn/";
        //指定html字符串打印
        String htmlDocument = "<html><body><h1>Test Content测试打印，测试打印</h1><p>Testing, testing, testing...测试测试测试测试</p></body></html>";
        if (printType==0){
            webView.loadUrl(htmlUrl);
        }else if (printType==1){
            webView.loadDataWithBaseURL(null, htmlDocument,"text/HTML","UTF-8",null);
        }else if (printType==2){
            //如果希望打印的页面含有图片，那就把要显示的图片放入工程的assets/目录下,
            webView.loadDataWithBaseURL("file:///android_asset/images/ic_launcher.png",htmlDocument,"text/HTML","UTF-8",null);
        }
    }
    private void createWebPrintJob(WebView webView){
        //首先创建一个打印管理器对象并实例化
        PrintManager printManager= (PrintManager)getSystemService(Context.PRINT_SERVICE);
        //获取打印适配器实例
        PrintDocumentAdapter pDAdapter=webView.createPrintDocumentAdapter();
        //使用名称和适配起来打印名称
        String jobName=getString(R.string.app_name)+"Document";
        printManager.print(jobName,pDAdapter,new PrintAttributes.Builder().build());
        printListener(printManager);
    }

    /**
     * 自定义打印
     */
    private void doPrint(String filePath){
        // Get a PrintManager instance 获取打印驱动对象
        PrintManager printManager= (PrintManager) getSystemService(Context.PRINT_SERVICE);
        // Set job name, which will be displayed in the print queue 设置作业名称，该名称将显示在打印队列中
        String jobName=getString(R.string.app_name)+"Document";
        // Start a print job, passing in a PrintDocumentAdapter implementation 启动打印作业，传入printdocumentadapter实现
        // to handle the generation of a print document处理打印文档的生成
//        Toast.makeText(context, "打印文件路径："+filePath, Toast.LENGTH_SHORT).show();
        printManager.print(jobName,new MyPrintDocumentAdapter(context,filePath),null);
        printListener(printManager);
    }

    /**
     * 创建打印适配器
     */
    private class MyPrintDocumentAdapter extends PrintDocumentAdapter {
        private Context mContext;
        private String mFilePath;
        private PrintedPdfDocument mPdfDocument;

        public PdfDocument myPdfDocument;
        public int totalpages = 1;//设置一共打印一张纸

        public MyPrintDocumentAdapter(Context context, String filePath) {
            this.mContext = context;
            this.mFilePath = filePath;
        }

        //当打印进程开始，该方法就将被调用，
        @Override
        public void onStart() {
            Toast.makeText(mContext, "准备开始", Toast.LENGTH_SHORT).show();
            super.onStart();
        }

        //当用户改变了打印输出时，比方说页面尺寸，或者页面的方向时，
        // 该函数将被调用。以此会给我们的应用重新计划打印页面的布局，
        // 另外该方法必须返回打印文档包含多少页面。
        @Override
        public void onLayout(PrintAttributes printAttributes,
                             PrintAttributes printAttributes1,
                             CancellationSignal cancellationSignal,
                             LayoutResultCallback layoutResultCallback,
                             Bundle bundle) {
//            //使用请求的页属性创建新的pdfdocument
//            mPdfDocument=new PrintedPdfDocument(mContext,printAttributes1);
            // 响应取消请求
            if (cancellationSignal.isCanceled()) {
                layoutResultCallback.onLayoutCancelled();
                return;
            }
            // 将打印信息返回到打印框架
            PrintDocumentInfo info = new PrintDocumentInfo
                    .Builder("name")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build();
            layoutResultCallback.onLayoutFinished(info, true);
        }

        //此函数被调用后，会将打印页面渲染成一个待打印的文件，该函数
        // 可以在onLayout被调用后调用一次或多次
        @Override
        public void onWrite(PageRange[] pageRanges,
                            ParcelFileDescriptor parcelFileDescriptor,
                            CancellationSignal cancellationSignal,
                            WriteResultCallback writeResultCallback) {
            InputStream input = null;
            OutputStream output = null;

            try {
                input = new FileInputStream(mFilePath);
                output = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());
                byte[] buf = new byte[1024];
                int bytesRead;
                while ((bytesRead = input.read(buf)) > 0) {
                    output.write(buf, 0, bytesRead);
                }
                writeResultCallback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Toast.makeText(mContext, "待打印状态", Toast.LENGTH_SHORT).show();

        }

        //一旦打印进程结束后，该函数将会被调用。如果我们的应用有任何
        // 一次性销毁任务要执行，让这些任务在该方法内执行。这个回调方法不是必须实现的。
//        @Override
//        public void onFinish() {
//            Toast.makeText(mContext, "已发送打印", Toast.LENGTH_SHORT).show();
//            super.onFinish();
//        }
    }



    private void printListener(final PrintManager printManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            printJob = printManager.getPrintJobs().get(printManager.getPrintJobs().size() - 1);
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    System.out.println(con++);
                    System.out.println("testPrint1======printJob  info=======" + printJob.getInfo());
                    System.out.println("testPrint2======printJob  id=======" + printJob.getId());
                    System.out.println("testPrint3======printJob  isBlocked=======" + printJob.isBlocked());
                    System.out.println("testPrint4======printJob  isCancelled=======" + printJob.isCancelled());
                    System.out.println("testPrint5======printJob  isCompleted=======" + printJob.isCompleted());
                    System.out.println("testPrint6======printJob  isFailed=======" + printJob.isFailed());
                    System.out.println("testPrint7======printJob  isQueued=======" + printJob.isQueued());

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                        if (printJob.isStarted()) {
//                            System.out.println("执行启动打印");
//                            Toast.makeText(context, "程序已将打印作业发送给打印机", Toast.LENGTH_SHORT).show();
//                            handler.sendEmptyMessage(FILE_SET);
//                            return;
////                            打印完成
//                        } else if (printJob.isCompleted()){
//                            Toast.makeText(context, "已完成打印", Toast.LENGTH_SHORT).show();
//                            handler.sendEmptyMessage(PRINT_COMPLETED);
//                        }
//                        打印作业是否已完成
                        if (printJob.isCompleted()) {
                            System.out.println("执行打印完成");
                            LemonBubble.hide();
                            handler.sendEmptyMessage(PRINT_COMPLETED);
                            return;
//                            是否取消打印作业
                        } else if (printJob.isCancelled()) {
                            System.out.println("执行取消打印");
                            handler.sendEmptyMessage(PRINT_CANCEL);
//                            return;
//                            获取此打印作业是否被阻止。 *由于异常情况，该打印作业被暂停*。您可以通过以下方式请求取消
                        } else if (printJob.isBlocked()) {
                            System.out.println("执行打印作业是否被阻止");
//                            打印作业是否失败
                        } else if (printJob.isFailed()) {
                            System.out.println("执行打印是否失败");
//                            是否启动打印作业
                        } else if (printJob.isStarted()) {
                            LemonBubble.showRoundProgress(context, "正在打印，请稍后...");
                            System.out.println("执行启动打印");
//                                打印作业是否已排队
                        } else if (printJob.isQueued()) {
                            System.out.println("执行是否已排队");
                        }else {
                            handler.postDelayed(this, 1000);
                        }
                        con++;
                    }
                }
            };
            runnable.run();
        }
    }

    /**
     * 打开权限
     */
    private void openPermissions(){
        final RxPermissions rxPermissions = new RxPermissions(MainActivity.this); // where this is an Activity or Fragment instance
        rxPermissions.requestEachCombined(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        ).subscribe(new Consumer<Permission>() {
            @Override
            public void accept(Permission permission) throws Exception {
                if (permission.granted){

                    Log.d("执行","权限都通过了");
                }else if (permission.shouldShowRequestPermissionRationale){
                    Log.d("执行","至少有一个权限被拒绝了");
                    openPermissions();
                }else {
                    Log.d("执行","转到设置");
                }
            }
        });
    }
}