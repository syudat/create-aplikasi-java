package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.view.*;
import android.view.ViewGroup.*;


public class SlidingLayout extends HorizontalScrollView
{
	/**
	 * Lebar layar
	 */
	private int mScreenWidth;
	/**
	 * Rentang geser yang dapat membuka menu
	 */
	private int mTouchScale = 0;
	/**
	 * Lebar menu
	 */
	private int mMenuWidth = 0;
	private int mHalfMenuWidth = 0;

	private boolean once;

	private boolean isOpen;

	private LinearLayout wrapper;

	private OnMenuOpenedListener mOnMenuOpenedListener;

	private OnMenuClosedListener mOnMenuClosedListener;

	private OnMenuStateChangeListener mOnMenuStateChangeListener;

	private boolean isSlidinged;

	public SlidingLayout(Context context)
	{
		super(context);
		init(context);
	}

	public SlidingLayout(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init(context);
	}

	public void setOnMenuStateChangeListener(OnMenuStateChangeListener mOnMenuStateChangeListener)
	{
		this.mOnMenuStateChangeListener = mOnMenuStateChangeListener;
	}

	public void setOnMenuClosedListener(OnMenuClosedListener onMenuClosedListener)
	{
		mOnMenuClosedListener = onMenuClosedListener;
	}

	public void setOnMenuOpenedListener(OnMenuOpenedListener onMenuOpenedListener)
	{
		mOnMenuOpenedListener = onMenuOpenedListener;
	}

	public void setTouchScale(int touchScale)
	{
		this.mTouchScale = touchScale;
	}

	public int getTouchScale()
	{
		return mTouchScale;
	}

	public void setMenuWidth(int menuWidth)
	{
		this.mMenuWidth = menuWidth;
	}

	public int getMenuWidth()
	{
		return mMenuWidth;
	}

	private void init(Context context)
	{
		setHorizontalScrollBarEnabled(false);
		mScreenWidth = context.getResources().getDisplayMetrics().widthPixels;

		//Secara default rentang geser sisi kiri adalah 10% lebar layar
		mTouchScale = mScreenWidth / 10;
		wrapper = new LinearLayout(context);
		super.addView(wrapper);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		/**
		 * Atur lebar menu
		 */
		if (!once)
		{
			View menu = wrapper.getChildAt(0);
			View content = wrapper.getChildAt(1);

			ViewGroup.LayoutParams lp=menu.getLayoutParams();

			//Jika lebar menu tidak ditentukan, gunakan 80% lebar layar
			if (mMenuWidth == 0 && lp.width < 0)
				lp.width = (int)(mScreenWidth * 0.8);
			mMenuWidth = lp.width;
			mHalfMenuWidth = lp.width / 2;
			content.getLayoutParams().width = mScreenWidth;
			wrapper.getLayoutParams().width=mScreenWidth+mMenuWidth;
		}
		if(isOpen)
			openMenu();
		else
			closeMenu();
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		super.onLayout(changed, l, t, r, b);
		if (changed)
		{
			// Sembunyikan menu
			if(!once)
				this.scrollTo(mMenuWidth, 0);
			else
				closeMenu();
			once = true;
		}
		
	}

	@Override
	public void addView(View child, ViewGroup.LayoutParams params)
	{
		// TODO: Implement this method
		wrapper.addView(child, params);
	}

	@Override
	public void addView(View child)
	{
		// TODO: Implement this method
		wrapper.addView(child);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev)
	{
		//Jika menu tampil atau sentuhan berada dalam rentang, cegat operasi sentuh
		if (isOpen || ev.getX() < mTouchScale)
		{
			return super.onInterceptTouchEvent(ev);
		}
		else
		{
			return false;
		}
	}


	@Override
	public boolean onTouchEvent(MotionEvent ev)
	{
		int action = ev.getAction();
		switch (action)
		{
			case MotionEvent.ACTION_MOVE:
				if (!isSlidinged && mOnMenuStateChangeListener != null)
					mOnMenuStateChangeListener.onMenuStateChange(this, isOpen);
				isSlidinged = true;
				break;
				// Saat Up, periksa: jika area yang tampil lebih dari setengah lebar menu maka tampilkan penuh, jika tidak sembunyikan
			case MotionEvent.ACTION_UP:
				int scrollX = getScrollX();
				if (isOpen)
				{
					if (scrollX > mHalfMenuWidth / 2)
						closeMenu();
					else
						openMenu();
				}
				else
				{
					if (scrollX > mHalfMenuWidth * 1.5)
						closeMenu();
					else
						openMenu();
				}
				isSlidinged = false;
				return true;
		}
		return super.onTouchEvent(ev);
	}

	/**
	 * Buka menu
	 */
	public void openMenu()
	{
		this.smoothScrollTo(0, 0);
		if (!isOpen && mOnMenuOpenedListener != null)
			mOnMenuOpenedListener.onMenuOpened(this);
		isOpen = true;
	}

	/**
	 * Tutup menu
	 */
	public void closeMenu()
	{
		this.smoothScrollTo(mMenuWidth, 0);
		if (isOpen && mOnMenuClosedListener != null)
			mOnMenuClosedListener.onMenuClosed(this);
		isOpen = false;
	}

	/**
	 * Ganti status menu
	 */
	public void toggle()
	{
		if (isOpen)
		{
			closeMenu();
		}
		else
		{
			openMenu();
		}
	}

	public static interface OnMenuOpenedListener
	{
		public void onMenuOpened(View v);
	}

	public static interface OnMenuClosedListener
	{
		public void onMenuClosed(View v);
	}

	public static interface OnMenuStateChangeListener
	{
		public void onMenuStateChange(View v, boolean isOpen);
	}
}
