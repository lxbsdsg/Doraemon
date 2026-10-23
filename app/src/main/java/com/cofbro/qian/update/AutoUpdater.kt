package com.cofbro.qian.update

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import com.alibaba.fastjson.JSON
import com.cofbro.hymvvmutils.base.getBySp
import com.cofbro.hymvvmutils.base.saveUsedSp
import com.cofbro.qian.utils.Constants
import com.cofbro.qian.utils.parse2Long
import com.cofbro.qian.view.dialog.AutoUpdateTipDialog
import com.cofbro.qian.view.dialog.DownloadDialog
import com.hjq.toast.ToastUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL


class AutoUpdater(private val mContext: Context) {
    private var apkFile = File(mContext.filesDir, "app-release-c.apk")
    private var apkUrl = ""
    private val checkUrl = "https://api.github.com/repos/CofbroTeam/Doraemon/releases/latest"
    private val releasesPageUrl = "https://github.com/CofbroTeam/Doraemon/releases"
    private var intercept = false
    private var downLoadThread: Thread? = null
    private var mProgress: ProgressBar? = null
    private var progress = 0f
    private var mTextStatus: TextView? = null
    private var mHandler: DownloadHandler? = null
    private val downApkWork = Runnable {
        try {
            val url = URL(apkUrl)
            HttpURLConnection.setFollowRedirects(true)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.connect()
            val length = if (conn.contentLength > 0) conn.contentLength else 1
            val ins = conn.inputStream
            val fos = FileOutputStream(apkFile)
            var count = 0
            val buf = ByteArray(1024)
            while (true) {
                if (intercept) {
                    break
                }
                val numRead = ins.read(buf)
                if (numRead <= 0) {
                    fos.flush()
                    mHandler?.sendEmptyMessage(DOWN_OVER)
                    break
                }
                count += numRead
                progress = count * 100f / length
                mHandler?.sendEmptyMessage(DOWN_UPDATE)
                fos.write(buf, 0, numRead)
            }
            fos.close()
            ins.close()
        } catch (e: Exception) {
            e.printStackTrace()
            mHandler?.obtainMessage(DOWN_CHECK_FAILED, "下载失败，请前往 GitHub Releases 手动下载")?.sendToTarget()
        }
    }

    fun checkUpdate(
        forced: Boolean = false,
        onPreCheck: () -> Unit = {},
        onShowDownloadDialog: () -> Unit = {},
        onCheckFinished: () -> Unit = {}
    ) {
        onPreCheck.invoke()
        mHandler = DownloadHandler(this)
        Thread(Runnable {
            var localVersion = "1"
            try {
                localVersion =
                    mContext.packageManager.getPackageInfo(mContext.packageName, 0).versionName
                mContext.saveUsedSp(Constants.Update.LOCAL_VERSION, localVersion)
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            val config = doGet(checkUrl)
            if (config.isNullOrEmpty()) {
                mHandler?.obtainMessage(DOWN_CHECK_FAILED, "检查更新失败，请检查网络或前往 GitHub Releases 手动下载")?.sendToTarget()
                invokeOnMain(onCheckFinished)
                return@Runnable
            }
            var remoteVersion = "0"
            var apkAssetUrl = ""
            try {
                val release = JSON.parseObject(config)
                remoteVersion = release.getString("published_at")
                    ?.replace(Regex("[^0-9]"), "")
                    ?.take(14)
                    ?.ifEmpty { "0" } ?: "0"
                val assets = release.getJSONArray("assets")
                for (index in 0 until (assets?.size ?: 0)) {
                    val asset = assets?.getJSONObject(index) ?: continue
                    val name = asset.getString("name") ?: ""
                    if (name.endsWith(".apk", true)) {
                        apkAssetUrl = asset.getString("browser_download_url") ?: ""
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val tipShow = mContext.getBySp(Constants.Update.UPDATE_TIP)
            if (tipShow == Constants.Update.NO_SHOW && !forced) {
                invokeOnMain(onCheckFinished)
                return@Runnable
            }
            mContext.saveUsedSp(Constants.Update.REMOTE_VERSION, remoteVersion)
            if (apkAssetUrl.isNotEmpty() && checkIfValidVersion(remoteVersion)) {
                apkUrl = apkAssetUrl
                mHandler?.sendEmptyMessage(DOWN_START)
                (mContext as? Activity)?.runOnUiThread(onShowDownloadDialog)
            } else {
                mHandler?.obtainMessage(DOWN_NO_UPDATE, releasesPageUrl)?.sendToTarget()
                invokeOnMain(onCheckFinished)
            }
        }).start()
    }

    private fun invokeOnMain(action: () -> Unit) {
        val activity = mContext as? Activity
        if (activity != null) {
            activity.runOnUiThread(action)
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    private fun checkIfValidVersion(remoteVersion: String): Boolean {
        val remoteUpdateVersion = parse2Long(remoteVersion)
        val localUpdateVersion = parse2Long(mContext.getBySp(Constants.Update.LOCAL_VERSION))
        if (localUpdateVersion == 0L || localUpdateVersion < remoteUpdateVersion) {
            return true
        }
        if (localUpdateVersion == remoteUpdateVersion) {
            apkFile.delete()
        }
        return false
    }

    private fun downloadApk() {
        val thread = Thread(downApkWork)
        downLoadThread = thread
        thread.start()
    }

    private fun doGet(httpUrl: String?): String? {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var br: BufferedReader? = null
        var result: String? = null
        try {
            val url = URL(httpUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connect()
            if (connection.responseCode == 200) {
                inputStream = connection.inputStream
                br = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val sbf = StringBuffer()
                var temp: String? = null
                while (br.readLine().also { temp = it } != null) {
                    sbf.append(temp)
                    sbf.append("\r\n")
                }
                result = sbf.toString()
            }
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                br?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            try {
                inputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            connection?.disconnect()
        }
        return result
    }

    @SuppressLint("SetTextI18n")
    private fun updateDownloadProgress() {
        val formattedNum = String.format("%.2f", progress)
        mTextStatus?.text = "$formattedNum%"
        mProgress?.progress = progress.toInt()
    }

    private fun installAPK() {
        try {
            if (!apkFile.exists()) {
                return
            }
            val intent = Intent("android.intent.action.VIEW")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= 24) {
                val packageName = mContext.applicationContext.packageName
                val authority = "$packageName.fileprovider"
                val apkUri = FileProvider.getUriForFile(
                    mContext, authority,
                    apkFile
                )
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            } else {
                intent.setDataAndType(
                    Uri.fromFile(apkFile),
                    "application/vnd.android.package-archive"
                )
            }
            mContext.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showUpdateDialog() {
        AutoUpdateTipDialog(mContext).apply {
            show()
            setOnPositiveListener {
                showDownloadDialog()
                dismiss()
            }
            setOnNegativeListener {
                dismiss()
            }
        }
    }

    private fun showDownloadDialog() {
        DownloadDialog(mContext).apply {
            setOnNegativeButtonListener {
                intercept = true
                dismiss()
            }
            setCancelable(false)
            show()
            mProgress = progress
            mTextStatus = textStatus
        }
        downloadApk()
    }

    companion object {
        private const val DOWN_UPDATE = 1
        private const val DOWN_OVER = 2
        private const val DOWN_START = 3
        private const val DOWN_CHECK_FAILED = 4
        private const val DOWN_NO_UPDATE = 5
    }

    private class DownloadHandler(updater: AutoUpdater) : Handler(Looper.getMainLooper()) {
        private val mUpdateRef: WeakReference<AutoUpdater> = WeakReference(updater)

        override fun handleMessage(msg: Message) {
            val mUpdater = mUpdateRef.get() ?: return

            when (msg.what) {
                DOWN_UPDATE -> {
                    mUpdater.updateDownloadProgress()
                }

                DOWN_OVER -> {
                    ToastUtils.show("下载完毕")
                    mUpdater.installAPK()
                }

                DOWN_START -> {
                    mUpdater.showUpdateDialog()
                }

                DOWN_CHECK_FAILED -> {
                    ToastUtils.show(msg.obj as? String ?: "检查更新失败")
                }

                DOWN_NO_UPDATE -> {
                    ToastUtils.show("已是最新版本（原作者已停止更新）")
                }
            }
        }
    }
}
