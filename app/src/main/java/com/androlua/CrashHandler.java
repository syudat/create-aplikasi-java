package com.androlua;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.util.*;

/**
 * Kelas penanganan UncaughtException; saat terjadi exception yang tidak tertangkap, kelas ini mengambil alih program dan mencatat laporan kesalahan.
 * 
 * @author user
 * 
 */
public class CrashHandler implements UncaughtExceptionHandler
{

	public static final String TAG = "CrashHandler";

	//Penanganan UncaughtException default sistem 
	private UncaughtExceptionHandler mDefaultHandler;
	//Instance CrashHandler
	private static CrashHandler INSTANCE = new CrashHandler();
	//Objek Context aplikasi
	private Context mContext;
	//Untuk menyimpan informasi perangkat dan exception
	private Map<String, String> infos = new LinkedHashMap<String, String>();

	//Untuk memformat tanggal, sebagai bagian dari nama file log
	private DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

	/** Memastikan hanya ada satu instance CrashHandler */
	private CrashHandler()
	{
	}

	/** Mendapatkan instance CrashHandler, pola singleton */
	public static CrashHandler getInstance()
	{
		return INSTANCE;
	}

	/**
	 * Inisialisasi
	 * 
	 * @param context
	 */
	public void init(Context context)
	{
		mContext = context;
		//Ambil penangan UncaughtException default sistem
		mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		//Atur CrashHandler ini sebagai penangan default aplikasi
		Thread.setDefaultUncaughtExceptionHandler(this);
	}

	/**
	 * Saat UncaughtException terjadi, penanganan dialihkan ke fungsi ini
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable ex)
	{
		if (!handleException(ex) && mDefaultHandler != null)
		{
			//Jika tidak ditangani pengguna, serahkan ke penangan exception default sistem
			mDefaultHandler.uncaughtException(thread, ex);
		}
		else
		{
			/*try
			{
				Thread.sleep(3000);
			}
			catch (InterruptedException e)
			{
				Log.e(TAG, "error : ", e);
			}
			//Keluar dari program
			android.os.Process.killProcess(android.os.Process.myPid());
			System.exit(0);*/
		}
	}

	/**
	 * Penanganan kesalahan kustom; pengumpulan informasi kesalahan, pengiriman laporan, dan sebagainya dilakukan di sini.
	 * 
	 * @param ex
	 * @return true jika exception ini sudah ditangani; selain itu false.
	 */
	private boolean handleException(Throwable ex)
	{
		if (ex == null)
		{
			return false;
		}
		//Tampilkan informasi exception dengan Toast
		/*new Thread() {
			@Override
			public void run()
			{
				Looper.prepare();
				Toast.makeText(mContext, "Maaf, terjadi kesalahan pada aplikasi dan akan segera ditutup.", Toast.LENGTH_LONG).show();
				Looper.loop();
			}
		}.start();*/
		//Kumpulkan informasi parameter perangkat 
		collectDeviceInfo(mContext);
		//Simpan file log 
		saveCrashInfo2File(ex);
		// Analitik + App Quality Insights: catat crash ke data lokal
		// (tidak dikirim ke mana pun); gagal mencatat tidak boleh
		// menutupi penanganan crash asli.
		try {
			String jejak = "";
			try {
				Writer w = new StringWriter();
				PrintWriter pw = new PrintWriter(w);
				ex.printStackTrace(pw);
				pw.close();
				jejak = w.toString();
			} catch (Throwable ignored) {
			}
			String tanggal = "";
			try {
				tanggal = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
			} catch (Throwable ignored) {
			}
			if (mContext != null) {
				String layar = AnalitikActivity.layarTerakhir(mContext);
				AnalitikActivity.catatCrash(mContext, tanggal, layar, ex.toString(), jejak);
			}
		} catch (Throwable ignored) {
		}
		return true;
	}

	/**
	 * Mengumpulkan informasi parameter perangkat
	 * @param ctx
	 */
	public void collectDeviceInfo(Context ctx)
	{
		try
		{
			PackageManager pm = ctx.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);
			if (pi != null)
			{
				String versionName = pi.versionName == null ? "null" : pi.versionName;
				String versionCode = pi.versionCode + "";
				infos.put("versionName", versionName);
				infos.put("versionCode", versionCode);
			}
		}
		catch (NameNotFoundException e)
		{
			Log.e(TAG, "an error occured when collect package info", e);
		}
		Field[] fields = Build.class.getDeclaredFields();
		for (Field field : fields)
		{
			try
			{
				field.setAccessible(true);
				Object obj=field.get(null);
				if(obj instanceof String[])
					infos.put(field.getName(), Arrays.toString((String[])obj));
				else
					infos.put(field.getName(), obj.toString());
				Log.d(TAG, field.getName() + " : " + field.get(null));
			}
			catch (Exception e)
			{
				Log.e(TAG, "an error occured when collect crash info", e);
			}
		}
		fields = Build.VERSION.class.getDeclaredFields();
		for (Field field : fields)
		{
			try
			{
				field.setAccessible(true);
				Object obj=field.get(null);
				if(obj instanceof String[])
					infos.put(field.getName(), Arrays.toString((String[])obj));
				else
					infos.put(field.getName(), obj.toString());
				Log.d(TAG, field.getName() + " : " + field.get(null));
			}
			catch (Exception e)
			{
				Log.e(TAG, "an error occured when collect crash info", e);
			}
		}
	}

	/**
	 * Menyimpan informasi kesalahan ke file
	 * 
	 * @param ex
	 * @return mengembalikan nama file, memudahkan pengiriman file ke server
	 */
	private String saveCrashInfo2File(Throwable ex)
	{

		StringBuffer sb = new StringBuffer();
		for (Map.Entry<String, String> entry : infos.entrySet())
		{
			String key = entry.getKey();
			String value = entry.getValue();
			sb.append(key + "=" + value + "\n");
		}

		Writer writer = new StringWriter();
		PrintWriter printWriter = new PrintWriter(writer);
		ex.printStackTrace(printWriter);
		Throwable cause = ex.getCause();
		while (cause != null)
		{
			cause.printStackTrace(printWriter);
			cause = cause.getCause();
		}
		printWriter.close();
		String result = writer.toString();
		sb.append(result);
		try
		{
			long timestamp = System.currentTimeMillis();
			String time = formatter.format(new Date());
			String fileName = "crash-" + time + "-" + timestamp + ".log";
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) 
			{
				String path = "/sdcard/androlua/crash/";
				File dir = new File(path);
				if (!dir.exists())
					dir.mkdirs();

				FileOutputStream fos = new FileOutputStream(path + fileName);
//				FileOutputStream fos = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
				fos.write(sb.toString().getBytes());
				Log.e("crash",sb.toString());
				
				fos.close();
			}
			return fileName;
		}
		catch (Exception e)
		{
			Log.e(TAG, "an error occured while writing file...", e);
		}
		return null;
	}
}
