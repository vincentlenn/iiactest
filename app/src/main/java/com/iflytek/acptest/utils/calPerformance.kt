package com.iflytek.acptest.utils

import android.icu.text.NumberFormat
import com.iflytek.acptest.MainActivity.Companion.itest_store_path
import java.io.File

class CalPerformance {
    fun calPerfData(toFile: String, timestamp: String) {
        val path = itest_store_path + File.separatorChar + "handTest-" + timestamp
        val files: MutableList<File> = mutableListOf()
        val fileTree: FileTreeWalk = File(path).walk()
        fileTree.maxDepth(1) //遍历的目录层级为1
            .filter { it.isFile } //过滤目录
            .filter { it.extension == "txt" } //过滤非txt的文件
            .forEach { files.add(it) }
        FileHandler.writeContents(toFile, "\n[性能指标]")
        if (files.isNotEmpty()) {
            for (f in files) {
                var collector: MutableList<Float> = mutableListOf()
                for (line in f.readLines()) {
                    try {
                        val value = line.split("\t")[1].toFloat()
                        if (value > 0) {
                            collector.add(value)
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                val nf = NumberFormat.getNumberInstance()
                nf.maximumFractionDigits = 2
                val contents =
                    "${f.nameWithoutExtension}: Max: ${collector.max()}, Min: ${collector.min()}, Average: ${nf.format(collector.average())}"
                FileHandler.writeContents(toFile, contents)
            }
        } else {
            FileHandler.writeContents(toFile, "No files under the path.")
        }
    }
}