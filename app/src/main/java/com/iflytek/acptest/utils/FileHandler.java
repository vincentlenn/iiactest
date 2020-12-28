package com.iflytek.acptest.utils;

import android.os.Environment;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class FileHandler {

    public static void writeContents(String filePath, String content) throws IOException {
        String inContents = content + "\r\n";
        String temp = "";
        FileInputStream fis = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        FileOutputStream fos  = null;
        PrintWriter pw = null;

        try {
            File file = new File(filePath);
            fis = new FileInputStream(file);
            isr = new InputStreamReader(fis);
            br = new BufferedReader(isr);
            StringBuffer buffer = new StringBuffer();
            // 读出文件原内容
            for(int i=0;(temp =br.readLine())!=null;i++){
                buffer.append(temp);
                // 行与行之间的分隔符 相当于“\n”
                buffer = buffer.append(System.getProperty("line.separator"));
            }
            buffer.append(inContents);

            fos = new FileOutputStream(file);
            pw = new PrintWriter(fos);
            pw.write(buffer.toString().toCharArray());
            pw.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (pw != null) {
                pw.close();
            }
            if (fos != null) {
                fos.close();
            }
            if (br != null) {
                br.close();
            }
            if (isr != null) {
                isr.close();
            }
            if (fis != null) {
                fis.close();
            }
        }
    }

    public static void copyFile(File source, File target) throws IOException {
        // 新建文件输入流并对它进行缓冲
        FileInputStream input = new FileInputStream(source);
        BufferedInputStream inBuff=new BufferedInputStream(input);
        // 新建文件输出流并对它进行缓冲
        FileOutputStream output = new FileOutputStream(target);
        BufferedOutputStream outBuff=new BufferedOutputStream(output);
        // 缓冲数组
        byte[] b = new byte[1024 * 5];
        int len;
        while ((len = inBuff.read(b)) != -1) {
            outBuff.write(b, 0, len);
        }
        // 刷新此缓冲的输出流
        outBuff.flush();
        //关闭流
        inBuff.close();
        outBuff.close();
        output.close();
        input.close();
    }

    public static void copyDirectory(String source, String target) throws IOException {
        String dir = target + File.separatorChar + "handTest";
        (new File(dir)).mkdirs();
        File[] file = (new File(source)).listFiles();
        if (file != null ) {
            for (int i = 0; i < file.length; i++) {
                if (file[i].isFile()) {
                    copyFile(file[i], new File(dir + File.separatorChar + file[i].getName()));
                }
            }
            System.out.println("Copy handTest folder done.");
            FileHandler.logger("perf", "Copy handTest folder done.");
        } else {
            FileHandler.logger("perf", "There is no file under path: " + source);
        }
    }

    public static void logger(String tag, String content) throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS", Locale.CHINA).format(new Date());
        String log = timestamp + ": " + content;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        String root = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separatorChar + "acp.test.tool";
        String logFile = root + File.separatorChar + tag + "-log_" + date + ".txt";
        writeContents(logFile, log);
    }

}
