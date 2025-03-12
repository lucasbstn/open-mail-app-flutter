package com.homex.open_mail_app

import android.content.Context
import android.content.Intent
import android.content.pm.LabeledIntent
import android.net.Uri
import androidx.annotation.NonNull
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class OpenMailAppPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var applicationContext: Context

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "open_mail_app")
        channel.setMethodCallHandler(this)
        applicationContext = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "openMailApp" -> result.success(emailAppIntent(call.argument("nativePickerTitle") ?: ""))
            "openSpecificMailApp" -> result.success(call.argument<String>("name")?.let { specificEmailAppIntent(it) })
            "composeNewEmailInMailApp" -> result.success(
                composeNewEmailAppIntent(
                    call.argument("nativePickerTitle") ?: "",
                    call.argument("emailContent") ?: ""
                )
            )
            "composeNewEmailInSpecificMailApp" -> result.success(
                composeNewEmailInSpecificEmailAppIntent(
                    call.argument("name") ?: "",
                    call.argument("emailContent") ?: ""
                )
            )
            "getMainApps" -> result.success(Gson().toJson(getInstalledMailApps()))
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun emailAppIntent(@NonNull chooserTitle: String): Boolean {
        val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
        val packageManager = applicationContext.packageManager
        val activitiesHandlingEmails = packageManager.queryIntentActivities(emailIntent, 0)
        if (activitiesHandlingEmails.isNotEmpty()) {
            val firstEmailPackageName = activitiesHandlingEmails.first().activityInfo.packageName
            val firstEmailInboxIntent = packageManager.getLaunchIntentForPackage(firstEmailPackageName)
            val emailAppChooserIntent = Intent.createChooser(firstEmailInboxIntent, chooserTitle)
            emailAppChooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            applicationContext.startActivity(emailAppChooserIntent)
            return true
        }
        return false
    }

    private fun composeNewEmailAppIntent(@NonNull chooserTitle: String, @NonNull contentJson: String): Boolean {
        val emailContent = Gson().fromJson(contentJson, EmailContent::class.java)
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val packageManager = applicationContext.packageManager
        val activitiesHandlingEmails = packageManager.queryIntentActivities(emailIntent, 0)
        if (activitiesHandlingEmails.isNotEmpty()) {
            val emailAppChooserIntent = Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, emailContent.to.toTypedArray())
                putExtra(Intent.EXTRA_CC, emailContent.cc.toTypedArray())
                putExtra(Intent.EXTRA_BCC, emailContent.bcc.toTypedArray())
                putExtra(Intent.EXTRA_SUBJECT, emailContent.subject)
                putExtra(Intent.EXTRA_TEXT, emailContent.body)
            }, chooserTitle)
            emailAppChooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            applicationContext.startActivity(emailAppChooserIntent)
            return true
        }
        return false
    }

    private fun specificEmailAppIntent(name: String): Boolean {
        val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
        val packageManager = applicationContext.packageManager
        val activitiesHandlingEmails = packageManager.queryIntentActivities(emailIntent, 0)
        val activityHandlingEmail = activitiesHandlingEmails.firstOrNull { it.loadLabel(packageManager) == name }
            ?: return false
        val emailInboxIntent = packageManager.getLaunchIntentForPackage(activityHandlingEmail.activityInfo.packageName)
            ?: return false
        emailInboxIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        applicationContext.startActivity(emailInboxIntent)
        return true
    }

    private fun composeNewEmailInSpecificEmailAppIntent(@NonNull name: String, @NonNull contentJson: String): Boolean {
        val emailContent = Gson().fromJson(contentJson, EmailContent::class.java)
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val packageManager = applicationContext.packageManager
        val activitiesHandlingEmails = packageManager.queryIntentActivities(emailIntent, 0)
        val specificEmailActivity = activitiesHandlingEmails.firstOrNull { it.loadLabel(packageManager) == name }
            ?: return false
        val composeEmailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, emailContent.to.toTypedArray())
            putExtra(Intent.EXTRA_CC, emailContent.cc.toTypedArray())
            putExtra(Intent.EXTRA_BCC, emailContent.bcc.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, emailContent.subject)
            putExtra(Intent.EXTRA_TEXT, emailContent.body)
            setClassName(specificEmailActivity.activityInfo.packageName, specificEmailActivity.activityInfo.name)
        }
        composeEmailIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        applicationContext.startActivity(composeEmailIntent)
        return true
    }

    private fun getInstalledMailApps(): List<App> {
        val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
        val packageManager = applicationContext.packageManager
        val activitiesHandlingEmails = packageManager.queryIntentActivities(emailIntent, 0)
        return activitiesHandlingEmails.map { App(it.loadLabel(packageManager).toString()) }
    }
}

data class App(@SerializedName("name") val name: String)

data class EmailContent(
    @SerializedName("to") val to: List<String>,
    @SerializedName("cc") val cc: List<String>,
    @SerializedName("bcc") val bcc: List<String>,
    @SerializedName("subject") val subject: String,
    @SerializedName("body") val body: String
)
