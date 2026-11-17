package android.app;

import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import android.util.*;

public class FloatWindow {

	private Context mContext;

	private TitleBar mTitlebar;

	private WindowManager mWindowManager;

	private WindowManager.LayoutParams mLayoutParams;

	private LinearLayout mLayout;

	private FrameLayout content;

	private int textColor;

	private DisplayMetrics dm;

	private int mWidth;

	private int mHeight;

	public FloatWindow(Context context) {
		mContext = context;
		dm = context.getResources().getDisplayMetrics();
		mWidth = getWidth();
		mHeight = getHeight();
		init(context);
	}

	private void init(Context context) {
		// TODO: Implement this method
		mWindowManager = (WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
		mLayoutParams = new WindowManager.LayoutParams();
		mLayoutParams.format = PixelFormat.RGBA_8888;
		mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
		//mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
		mLayoutParams.type = WindowManager.LayoutParams.TYPE_PHONE ;
		mLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
		mLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		mLayout = new ContentView(context);
		mLayout.setPadding(dp(8), dp(8), dp(8), dp(8));
		mLayout.setOrientation(LinearLayout.VERTICAL);

		TypedArray array = context.getTheme().obtainStyledAttributes(new int[] {  
																		 android.R.attr.colorBackground, 
																		 android.R.attr.textColorPrimary, 
																	 }); 
		int backgroundColor = array.getColor(0, 0xFF00FF); 
		textColor = array.getColor(1, 0xFF00FF); 
		array.recycle();

		GradientDrawable gd=new GradientDrawable();
		gd.setColor(backgroundColor);
		gd.setCornerRadius(4);
		gd.setStroke(2, textColor);
		gd.setAlpha(0x88);
		mLayout.setBackgroundDrawable(gd);
		mWindowManager.addView(mLayout, mLayoutParams);
		mLayout.setVisibility(View.GONE);
		mTitlebar = new TitleBar(context);
		content = new FrameLayout(context);
		mLayout.addView(mTitlebar);
		mLayout.addView(content);
	}


	private int getWidth() {
		return mContext.getResources().getDisplayMetrics().widthPixels;
	}

	private int getHeight() {
		return mContext.getResources().getDisplayMetrics().heightPixels;
	}

	private void setFocus(boolean f) {
		if (f) {
			if (mLayoutParams.flags == (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)) {
				mWindowManager.removeView(mLayout);
				mWindowManager.addView(mLayout, mLayoutParams);
				mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
				mWindowManager.updateViewLayout(mLayout, mLayoutParams);
			}
		}
		else {
			mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
			mWindowManager.updateViewLayout(mLayout, mLayoutParams);
		}
	}

	private int dp(float n) {
		// TODO: Implement this method
		return (int)TypedValue.applyDimension(1, n, dm);
	}

	public void setBackground(Drawable bg) {
		mLayout.setBackgroundDrawable(bg);
	}

	public Drawable getBackground() {
		return mLayout.getBackground();
	}

	public void setTitle(CharSequence title) {
		mTitlebar.setTitle(title);
	}

	public void setContentView(View v) {
		content.removeAllViews();
		content.addView(v);
	}

	public void show() {
		mLayout.setVisibility(View.VISIBLE);
	}

	public void hide() {
		mLayout.setVisibility(View.GONE);
	}

	public void dismiss() {
		mWindowManager.removeView(mLayout);
	}

	public void setFlags(int flags) {
		mLayoutParams.flags = flags;
		mWindowManager.updateViewLayout(mLayout, mLayoutParams);
	}

	public void setFormat(int format) {
		mLayoutParams.format = format;
		mWindowManager.updateViewLayout(mLayout, mLayoutParams);
	}

	public void setType(int type) {
		mLayoutParams.type = type;
		mWindowManager.updateViewLayout(mLayout, mLayoutParams);
	}

	private class TitleBar extends LinearLayout {

		private TextView mTitle;
		public TitleBar(Context context) {
			super(context);
			mTitle = new TitleView(context);
			mTitle.setSingleLine(true);
			TextView mClose=new TextView(context);
			mClose.setText("X");
			mClose.setGravity(Gravity.CENTER);
			GradientDrawable gd=new GradientDrawable();
			gd.setColor(0x440000ff);
			gd.setCornerRadius(4);
			gd.setStroke(2, textColor);
			gd.setAlpha(0x88);
			mClose.setBackgroundDrawable(gd);
			mClose.setTextSize(1, 18);
			mClose.setOnClickListener(new OnClickListener(){

					@Override
					public void onClick(View p1) {
						// TODO: Implement this method
						dismiss();
					}
				});
			addView(mTitle, new LayoutParams(LayoutParams.MATCH_PARENT, dp(24), 1));
			addView(mClose, new LayoutParams(dp(24), dp(24)));
		}
		public void setTitle(CharSequence title) {
			mTitle.setText(title);
		}
	}

	private class TitleView extends TextView {

		private int lastX=0;
		private int lastY=0;
		private int vx=0;
		private int vy=0;
		private int h=0;
		private int ry;
		private int rx;

		public TitleView(Context context) {
			super(context);
		}

		@Override
		public boolean onTouchEvent(MotionEvent e) {
			// TODO: Implement this method
			Rect frame=new Rect();
			getWindowVisibleDisplayFrame(frame);
			h = frame.top;
			ry = (int) e.getRawY();//Ambil posisi Y absolut sentuhan
			rx = (int) e.getRawX();//Ambil posisi X absolut sentuhan
			if (e.getAction() == MotionEvent.ACTION_DOWN) {	
				vy = ry - (int) e.getY();//Ambil posisi Y view
				vx = rx - (int) e.getX();//Ambil posisi X view
				lastY = ry;//Catat posisi Y saat ditekan
				lastX = rx;//Catat posisi X saat ditekan
			}
			else if (e.getAction() == MotionEvent.ACTION_MOVE) {
				mLayoutParams.gravity = Gravity.LEFT | Gravity.TOP ;//Pindahkan jendela mengambang ke kiri atas
				mLayoutParams.x = vx + (rx - lastX);//Posisi relatif perpindahan
				mLayoutParams.y = vy + (ry - lastY) - h + 3;//Posisi relatif perpindahan
				mWindowManager.updateViewLayout(mLayout, mLayoutParams);//Atur jendela mengambang ke posisi yang ditentukan
			}
			return true;

			//return super.onTouchEvent(event);
		}

	}

	private class ContentView extends LinearLayout {

		private int lastX=0;
		private int lastY=0;
		private int ry;
		private int rx;

		private int vw;

		private int vh;

		private int vx;

		private int vy;

		private boolean zoomX;

		private int m;

		private boolean zoomY;

		public ContentView(Context context) {
			super(context);
			m = dp(8);
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent e) {
			// TODO: Implement this method
			if (e.getAction() == MotionEvent.ACTION_OUTSIDE) {
				setFocus(false);
			}
			else if (e.getAction() == MotionEvent.ACTION_DOWN) {	
				setFocus(true);
			}

			return super.onInterceptTouchEvent(e);
		}



		@Override
		public boolean onTouchEvent(MotionEvent e) {
			// TODO: Implement this method
			ry = (int) e.getRawY();//Ambil posisi Y absolut sentuhan
			rx = (int) e.getRawX();//Ambil posisi X absolut sentuhan
			if (e.getAction() == MotionEvent.ACTION_OUTSIDE) {
				setFocus(false);
			}
			else if (e.getAction() == MotionEvent.ACTION_DOWN) {	
				setFocus(true);
			}
			if (e.getAction() == MotionEvent.ACTION_DOWN) {	
				if (getWidth() - e.getX() < m) {
					zoomX = true;
				}
				if (getHeight() - e.getY() < m) {
					zoomY = true;
				}
				vw = getWidth();//Ambil posisi Y view
				vh = getHeight();//Ambil posisi X view
				lastY = ry;//Catat posisi Y saat ditekan
				lastX = rx;//Catat posisi X saat ditekan
				vx = mLayoutParams.x;
				vy = mLayoutParams.y;
			}
			else if (e.getAction() == MotionEvent.ACTION_MOVE) {
				mLayoutParams.x = vx;
				mLayoutParams.y = vy;
				if (zoomX)
					mLayoutParams.width = Math.min(vw + (rx - lastX), mWidth);//Lebar relatif perubahan
				if (zoomY)
					mLayoutParams.height = Math.min(vh + (ry - lastY), mHeight);//Tinggi relatif perubahan
				mWindowManager.updateViewLayout(mLayout, mLayoutParams);//Atur jendela mengambang ke ukuran yang ditentukan
			}
			else if (e.getAction() == MotionEvent.ACTION_UP) {
				zoomX=false;
				zoomY=false;
			}
			return true;
		}
	}
}
