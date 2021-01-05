package com.iflytek.acptest.utils;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;


@SuppressLint("SdCardPath")
public class calEngineTime {

    public static Boolean cal(String file,String target_log, String timestamp) throws Exception {
        /**
         * 应用native日志中打印的耗时指标
         * @一代版本
         * wait: 音频等待时长
         * raw: 裸引擎处理时长
         * acc: 结果处理耗时
         * cb_imr: 声强矩阵回调耗时
         * cb_dfa: 定向滤波音频回调耗时，在二代版本中不使用
         * cb_sd: 时频图回调耗时
         * all: 音频等待+引擎处理+结果处理的总时长，在二代版本中不使用
         * lost: UDP音频包丢包
         * discard: 业务主动丢弃UDP音频包
         *
         * @二代版本
         * wait: 音频等待时长
         * raw1,raw2: 裸引擎处理时长，双实例并行
         * cb_inv: 引擎返回计算结果的间隔耗时
         * cb_im: 声强矩阵回调耗时
         * cb_sp: 时频图回调耗时
         *
         * 应用android日志中打印的耗时指标:
         * @一代版本
         * generate: 热力图生成耗时
         * show: 热力图渲染耗时
         *
         * @二代版本
         * generate: 热力图生成耗时/间隔
         * matrix: 声强矩阵预处理耗时/间隔
         * Rate of audio data receiver: 音频数据接收速率
         * engine audio data lost rate: 引擎处理过慢时，音频缓冲区的溢出率
         * Heat map original data lost rate: 热力图丢帧率
         */
        String engine[][] = {
                {"native", "wait"},
                {"native", "raw1"},
                {"native", "raw2"},
                {"native", "cb_inv"},
                {"native", "cb_im"},
                {"native", "cb_sp"},
                {"android", "generate"},
                {"android", "matrix"},
                {"android", "intensity"}
        };

        String packages[][] = {
                {"native", "lost"},
                {"native", "discard"}
        };

        String rates[][] = {
                {"android", "Rate of audio data receiver"},
                {"android", "engine audio data lost rate"},
                {"android", "Heat map original data lost rate"}
        };

        FileHandler.writeContents(file, "\n[耗时指标]:");

        for (String item1[] : engine) {
            String category1 = item1[0];
            String title = item1[1];
            String Path1 = target_log + File.separator + category1 + "-" + timestamp;
            cal_engine_time(Path1, title, file);
        }

//        for (String item2[] : packages) {
//            String category2 = item2[0];
//            String keyword = item2[1];
//            String Path2 = target_log + File.separator + category2 + "-" + timestamp;
//            cal_package_lost(Path2, keyword, file);
//        }

        for (String item3[] : rates) {
            String category3 = item3[0];
            String keyline = item3[1];
            String Path3 = target_log + File.separator + category3 + "-" + timestamp;
            cal_rates(Path3, keyline, file);
        }

        Log.i(null, "calculate business data done");
        return true;
    }

    private static void cal_rates(String path3, String keyline, String file) throws Exception {
        float max = 0f;
        float min = Float.MAX_VALUE;
        float total = 0f;
        int count = 0;
        File files = new File(path3);
        File[] fs = files.listFiles();
        if (fs == null) {
            FileHandler.writeContents(file, "No files under the path: " + path3);
        }
        else {
            for (File f : fs) {
                try (BufferedReader bf = new BufferedReader(new FileReader(f))) {
                    String s = bf.readLine();
                    while (s != null) {
                        if (!s.equals("\n") && !s.isEmpty()) {
                            if (s.contains(keyline)) {
                                String temp = "";
                                if (keyline == "Rate of audio data receiver") {
                                    int costStartInd = s.lastIndexOf("is ") + 3;
                                    int costEndInd = s.lastIndexOf("MB/s");
                                    if (costStartInd < 0 || costEndInd < 0 || costEndInd <= costStartInd) {
                                        FileHandler.writeContents(file, "Warning: Invalid cost log found: " + s);
                                        s = bf.readLine();
                                        continue;
                                    }
                                    temp = s.substring(costStartInd, costEndInd);
                                } else {
                                    int costStartInd = s.lastIndexOf("is ") + 3;
                                    if (costStartInd < 0 || costStartInd >= s.length()) {
                                        FileHandler.writeContents(file, "Warning: Invalid cost log found: " + s);
                                        s = bf.readLine();
                                        continue;
                                    }
                                    temp = s.substring(costStartInd);
                                }
                                try {
                                    float i = Float.parseFloat(temp);
                                    max = Math.max(max, i);
                                    min = Math.min(min, i);
                                    count++;
                                    total += i;
                                } catch (NumberFormatException e) {
                                    FileHandler.logger("perf", "Exception thrown when parsing data to Float: " + e);
                                }
                            }
                        }
                        s = bf.readLine();
                    }
                }
            }

            if (min == Integer.MAX_VALUE && max == Integer.MIN_VALUE) {
                FileHandler.writeContents(file, keyline + ": Not found");
            } else {
                String res = keyline +
                        ": min: " + min + ", max: " + max +
                        ", count: " + count + ", average: " + total / (float)count;
                FileHandler.writeContents(file, res);
            }
        }
    }

    public static void cal_engine_time(String path, String title, String file) throws Exception{
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        long total = 0;
        int count = 0;
        int max_inv = Integer.MIN_VALUE;
        int min_inv = Integer.MAX_VALUE;
        long total_inv = 0;
        int count_inv = 0;
        File files = new File(path);
        File[] fs = files.listFiles();
        if (fs == null) {
            FileHandler.writeContents(file, "No files under the path: " + path);
        }
        else {
            for (File f : fs) {
                try (BufferedReader bf = new BufferedReader(new FileReader(f))) {
                    String s = bf.readLine();
                    while (s != null) {
                        if (!s.equals("\n") && !s.isEmpty()) {
                            if (s.contains(title + " engine time cost")) {
                                int costStartInd = s.lastIndexOf("[") + 1;
                                int costEndInd = s.lastIndexOf("us");
                                if (costStartInd < 0 || costEndInd < 0 || costEndInd <= costStartInd) {
                                    FileHandler.writeContents(file, "Warning: Invalid cost log found: " + s);
                                    s = bf.readLine();
                                    continue;
                                }
                                String temp = s.substring(costStartInd, costEndInd);
                                try {
                                    int i = Integer.parseInt(temp);
                                    max = Math.max(max, i);
                                    min = Math.min(min, i);
                                    count++;
                                    total += i;
                                } catch (NumberFormatException e) {
                                    FileHandler.logger("perf", "Exception thrown when parsing data to Int: " + e);
                                }
                            } else if (s.contains("heat map " + title)) {
                                int costStartInd = s.lastIndexOf(":");
                                int costEndInd = s.lastIndexOf("ms");
                                if (costStartInd < 0 || costEndInd < 0 || costStartInd >= s.length()) {
                                    FileHandler.writeContents(file, "Warning: Invalid cost log found: " + s);
                                    s = bf.readLine();
                                    continue;
                                }
                                String temp = s.substring(costStartInd + 2, costEndInd);
                                try {
                                    int i = Integer.parseInt(temp);
                                    if (s.contains("cost")) {
                                        max = Math.max(max, i);
                                        min = Math.min(min, i);
                                        count++;
                                        total += i;
                                    } else {
                                        max_inv = Math.max(max_inv, i);
                                        min_inv = Math.min(min_inv, i);
                                        count_inv++;
                                        total_inv += i;
                                    }
                                } catch (NumberFormatException e) {
                                    System.out.println("Exception thrown  :" + e);
                                    FileHandler.logger("perf", "Exception thrown when parsing data to Int: " + e);
                                }
                            } else if (s.contains("intensity " + title)) {
                                int costStartInd = s.lastIndexOf(":");
                                int costEndInd = s.lastIndexOf("ms");
                                if (costStartInd < 0 || costEndInd < 0 || costStartInd >= s.length()) {
                                    FileHandler.writeContents(file, "Warning: Invalid cost log found: " + s);
                                    s = bf.readLine();
                                    continue;
                                }
                                String temp = s.substring(costStartInd + 2, costEndInd);
                                try {
                                    int i = Integer.parseInt(temp);
                                    if (s.contains("cost")) {
                                        max = Math.max(max, i);
                                        min = Math.min(min, i);
                                        count++;
                                        total += i;
                                    } else {
                                        max_inv = Math.max(max_inv, i);
                                        min_inv = Math.min(min_inv, i);
                                        count_inv++;
                                        total_inv += i;
                                    }
                                } catch (NumberFormatException e) {
                                    System.out.println("Exception thrown  :" + e);
                                    FileHandler.logger("perf", "Exception thrown when parsing data to Int: " + e);
                                }
                            } else if (s.contains(title + " mat:")) {
                                int costStartInd = s.lastIndexOf(":") + 2;
                                int costEndInd = s.lastIndexOf("ms");
                                if (costStartInd < 0 || costEndInd < 0 || costStartInd >= s.length()) {
                                    FileHandler.writeContents(file, "Warning: Invalid cost log found: " + s);
                                    s = bf.readLine();
                                    continue;
                                }
                                String temp = s.substring(costStartInd, costEndInd);
                                try {
                                    int i = Integer.parseInt(temp);
                                    max = Math.max(max, i);
                                    min = Math.min(min, i);
                                    count++;
                                    total += i;
                                } catch (NumberFormatException e) {
                                    FileHandler.logger("perf", "Exception thrown when parsing data to Int: " + e);
                                }
                            }
                        }
                        s = bf.readLine();
                    }
                }
            }

            String res = "";
            if (path.contains("native")) {
                if (min == Integer.MAX_VALUE && max == Integer.MIN_VALUE) {
                    res = title + " cost time: Not found";
                } else {
                    res = title +
                                ": ENGINE TIME CONSUME: min: " + (float) min / 1000 + ", max: " + (float) max / 1000 +
                                ", count: " + count + ", average: " + (float) total /
                                (float) count / 1000;
                }
            } else if (title == "generate") {
                String res1 = "";
                String res2 = "";
                if (min == Integer.MAX_VALUE && max == Integer.MIN_VALUE) {
                    res1 = title + " cost time: Not found";
                } else {
                    res1 = title +
                                " COST: HEATMAP TIME: min: " + (float) min + ", max: " + (float) max +
                                ", count: " + count + ", average: " + (float) total /
                                (float) count;
                }
                if (min_inv == Integer.MAX_VALUE && max_inv == Integer.MIN_VALUE) {
                    res2 = title + " interval time: Not found";
                } else {
                    res2 = title + " INTERVAL: HEATMAP TIME: min: " + (float) min_inv +
                                ", max: " + (float) max_inv + ", count: " + count_inv + ", average: " +
                                (float) total_inv / (float) count_inv;
                }
                res = res1 + "\n" + res2;
            } else if (title == "matrix") {
                String res1 = "";
                String res2 = "";
                if (min == Integer.MAX_VALUE && max == Integer.MIN_VALUE) {
                    res1 = title + " cost time: Not found";
                } else {
                    res1 = title +
                            " COST: PREPROCESS TIME: min: " + (float) min + ", max: " + (float) max +
                            ", count: " + count + ", average: " + (float) total /
                            (float) count;
                }
                if (min_inv == Integer.MAX_VALUE && max_inv == Integer.MIN_VALUE) {
                    res2 = title + " interval time: Not found";
                } else {
                    res2 = title + " INTERVAL: PREPROCESS TIME: min: " + (float) min_inv +
                            ", max: " + (float) max_inv + ", count: " + count_inv + ", average: " +
                            (float) total_inv / (float) count_inv;
                }
                res = res1 + "\n" + res2;
            } else {
                if (min == Integer.MAX_VALUE && max == Integer.MIN_VALUE) {
                    res = title + " mat on engine cost time: Not found";
                } else {
                    res = title + ": MAT ON ENGINE: min: " + (float) min + ", max: " + (float) max +
                            ", count: " + count + ", average: " + (float) total /
                            (float) count;
                }
            }
            FileHandler.writeContents(file, res);
        }

    }

    public static void cal_package_lost(String path, String keyword, String file) throws IOException {
        ArrayList<String> arraylist = new ArrayList<>();
        File files = new File(path);
        File[] fs = files.listFiles();
        if (fs == null) {
            FileHandler.writeContents(file, "No files under the path: " + path);
        } else {
            for (File f : fs) {
                try (BufferedReader bf = new BufferedReader(new FileReader(f))) {
                    String s = bf.readLine();
                    while (s != null) {
                        if (!s.equals("\n") && !s.isEmpty()) {
                            if (s.contains(keyword + " package")) {
                                if (keyword == "lost") {
                                    arraylist.add(s.substring(s.indexOf("profiler") + 10, s.lastIndexOf("]")));
                                }
                                if (keyword == "discard") {
                                    arraylist.add(s.substring(s.indexOf("FixedBufDiscardPolicy") - 1));
                                }
                            }
                        }
                        s = bf.readLine();
                    }
                }
            }
            if (arraylist.size() == 0) {
                FileHandler.writeContents(file, keyword + " package: Not found");
            } else {
                String content = arraylist.get(arraylist.size() - 1);
                if (content.contains("lost package")) {
                    long lostPkg = Long.parseLong(content.substring(content.indexOf("lost package count") + 19, content.indexOf("] [received")));
                    long receivedPkg = Long.parseLong(content.substring(content.indexOf("received package count") + 23, content.lastIndexOf("] [lost")));
                    FileHandler.writeContents(file, "UDP package lost ratio: " + (float)lostPkg/(float)(lostPkg + receivedPkg));
                } else if (content.contains("discard package")) {
                    long discardPkg = Long.parseLong(content.substring(content.indexOf("discard package") + 17, content.lastIndexOf(", total")));
                    long totalPkg = Long.parseLong(content.substring(content.lastIndexOf("package") + 8, content.lastIndexOf("]")));
                    FileHandler.writeContents(file, "UDP package discarded ratio: " + (float)discardPkg/(float)totalPkg);
                }

            }
        }
    }

}
