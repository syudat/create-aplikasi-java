package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.webkit.WebView;

import java.util.Timer;
import java.util.TimerTask;


/**
 * Layout kustom untuk mengelola tiga subview: header tarik-bawah, pullableView yang berisi konten (bisa View apa pun yang mengimplementasikan Pullable),
 * dan satu footer tarik-atas. Penjelasan lengkap ada di bloghttp://blog.csdn.net/zhongkejingwang/article/details/38868463
 *
 * @author Chen Jing
 * @mod nirenr
 * Dimodifikasi agar cocok dipakai di androlua; malas menambah komentar
 */
@SuppressWarnings("deprecation")
public class PullingLayout extends RelativeLayout {
    public static final String TAG = "PullToRefreshLayout";
    // Status awal
    public static final int INIT = 0;
    // Lepas untuk menyegarkan
    public static final int RELEASE_TO_REFRESH = 1;
    // Sedang menyegarkan
    public static final int REFRESHING = 2;
    // Lepas untuk memuat
    public static final int RELEASE_TO_LOAD = 3;
    // Sedang memuat
    public static final int LOADING = 4;
    // Operasi selesai
    public static final int DONE = 5;
    // Berhasil disegarkan
    public static final int SUCCEED = 0;
    // Gagal menyegarkan
    public static final int FAIL = 1;
    // Tidak ada konten
    public static final int NOTHING = 2;
    // Jarak tarik ke bawah. Catatan: pullDownY dan pullUpY tidak mungkin sama-sama bukan 0
    public float pullDownY = 0;
    // Kecepatan gulir balik
    public float MOVE_SPEED = 10;
    // Status saat ini
    private int state = INIT;
    // Antarmuka callback penyegaran
    private OnRefreshListener mRefreshListener;
    // Koordinat Y saat ditekan, koordinat Y titik event sebelumnya
    private float downY, lastY;
    // Jarak tarik ke atas
    private float pullUpY = 0;
    // Jarak lepas untuk menyegarkan
    private float refreshDist = 200;
    // Jarak lepas untuk memuat
    private float loadmoreDist = 200;
    private MyTimer timer;
    // Layout pertama kali dijalankan
    private boolean isLayout = false;
    // Geser saat proses penyegaran
    private boolean isTouch = false;
    // Rasio jarak geser jari terhadap jarak geser header tarik-bawah, berubah mengikuti fungsi tangen
    private float radio = 2;

    // Animasi memutar panah tarik-bawah 180°
    private RotateAnimation rotateAnimation;
    // Animasi putar merata
    private RotateAnimation refreshingAnimation;

    // Header tarik-bawah
    private HeadView refreshView;
    // Panah tarik-bawah
    private View pullView;
    // Ikon sedang menyegarkan
    private View refreshingView;
    // Ikon hasil penyegaran
    private ImageView refreshStateImageView;
    // Hasil penyegaran: berhasil atau gagal
    private TextView refreshStateTextView;

    // Footer tarik-atas
    private FootView loadmoreView;
    // Panah tarik-atas
    private View pullUpView;
    // Ikon sedang memuat
    private View loadingView;
    // Ikon hasil muat
    private ImageView loadStateImageView;
    // Hasil muat: berhasil atau gagal
    private TextView loadStateTextView;
    private int mStateColor;
    // View yang mengimplementasikan Pullable
    private FrameLayout pullableLayout;
    private View pullableView;
    // Abaikan multi-sentuh
    private int mEvents;
    // Dua variabel ini mengontrol arah pull; tanpa kontrol, bila kondisi memungkinkan tarik atas dan bawah sekaligus, tarik bawah tidak akan bisa
    private boolean canPullDown = true;
    private boolean canPullUp = true;
    private Context mContext;
    private boolean mPullUp;
    private boolean mPullDown;
    private LayoutInflater mInflater;
    private OnLoadMoreListener mLoadMoreListener;
    private OnPullUpListener mPullUpListener;
    private OnPullDownListener mPullDownListener;
    private DisplayMetrics dm;
    private int mForegroundColor;
    private int mBackgroundColor;
    private String refresh_succeed = "Berhasil disegarkan";
    private String refresh_nothing = "Belum ada pembaruan";
    private String refresh_fail = "Gagal menyegarkan";
    private String load_succeed = "Berhasil dimuat";
    private String load_nothing = "Tidak ada konten lagi";
    private String load_fail = "Gagal memuat";
    private String pullup_to_load = "Tarik ke atas untuk memuat lebih banyak";
    private String release_to_refresh = "Lepas untuk menyegarkan";
    private String refreshing = "Menyegarkan...";
    private String release_to_load = "Lepas untuk memuat";
    private String pull_to_refresh = "Tarik ke bawah untuk menyegarkan";
    private String loading = "Memuat...";
    /**
     * Handler untuk menjalankan gulir balik otomatis
     */
    Handler updateHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            // Kecepatan pantulan bertambah seiring bertambahnya jarak tarik moveDeltaY
            MOVE_SPEED = (float) (8 + 5 * Math.tan(Math.PI / 2
                    / getMeasuredHeight() * (pullDownY + Math.abs(pullUpY))));
            if (!isTouch) {
                // Jika sedang menyegarkan dan tidak didorong ke atas, tahan di posisi dan tampilkan "Menyegarkan..."
                if (state == REFRESHING && pullDownY <= refreshDist) {
                    pullDownY = refreshDist;
                    timer.cancel();
                } else if (state == LOADING && -pullUpY <= loadmoreDist) {
                    pullUpY = -loadmoreDist;
                    timer.cancel();
                }

            }
            if (pullDownY > 0)
                pullDownY -= MOVE_SPEED;
            else if (pullUpY < 0)
                pullUpY += MOVE_SPEED;
            if (pullDownY < 0) {
                // Pantulan selesai
                pullDownY = 0;
                pullView.clearAnimation();
                // Saat header tarik-bawah disembunyikan, penyegaran mungkin masih berjalan; status hanya diubah bila bukan sedang menyegarkan
                if (state != REFRESHING && state != LOADING)
                    changeState(INIT);
                timer.cancel();
                requestLayout();
            }
            if (pullUpY > 0) {
                // Pantulan selesai
                pullUpY = 0;
                pullUpView.clearAnimation();
                // Saat footer tarik-atas disembunyikan, penyegaran mungkin masih berjalan; status hanya diubah bila bukan sedang menyegarkan
                if (state != REFRESHING && state != LOADING)
                    changeState(INIT);
                timer.cancel();
                requestLayout();
            }
            // Segarkan layout, onLayout akan dipanggil otomatis
            requestLayout();
            // Tidak ada tarikan atau pantulan sudah selesai
            if (pullDownY + Math.abs(pullUpY) == 0)
                timer.cancel();
        }

    };

    public PullingLayout(Context context) {
        super(context);
        initView(context);
    }

    public PullingLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public PullingLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }

    public String getRefreshSucceed() {
        return refresh_succeed;
    }

    public void setRefreshSucceed(String refresh_succeed) {
        this.refresh_succeed = refresh_succeed;
    }

    public String getRefreshNothing() {
        return refresh_nothing;
    }

    public void setRefreshNothing(String refresh_nothing) {
        this.refresh_nothing = refresh_nothing;
    }

    public String getRefreshFail() {
        return refresh_fail;
    }

    public void setRefreshFail(String refresh_fail) {
        this.refresh_fail = refresh_fail;
    }

    public String getLoadSucceed() {
        return load_succeed;
    }

    public void setLoadSucceed(String load_succeed) {
        this.load_succeed = load_succeed;
    }

    public String getLoadNothing() {
        return load_nothing;
    }

    public void setLoadNothing(String load_nothing) {
        this.load_nothing = load_nothing;
    }

    public String getLoadFail() {
        return load_fail;
    }

    public void setLoadFail(String load_fail) {
        this.load_fail = load_fail;
    }

    public String getPullupToLoad() {
        return pullup_to_load;
    }

    public void setPullupToLoad(String pullup_to_load) {
        this.pullup_to_load = pullup_to_load;
    }

    public String getReleaseToRefresh() {
        return release_to_refresh;
    }

    public void setReleaseToRefresh(String release_to_refresh) {
        this.release_to_refresh = release_to_refresh;
    }

    public String getRefreshing() {
        return refreshing;
    }

    public void setRefreshing(String refreshing) {
        this.refreshing = refreshing;
    }

    public String getReleaseToLoad() {
        return release_to_load;
    }

    public void setReleaseToLoad(String release_to_load) {
        this.release_to_load = release_to_load;
    }

    /**
     * Menyelesaikan operasi segarkan dan menampilkan hasilnya. Catatan: metode ini wajib dipanggil setelah penyegaran selesai
     */

    public String getPulldownToRefresh() {
        return pull_to_refresh;
    }

    public void setPulldownToRefresh(String pull_to_refresh) {
        this.pull_to_refresh = pull_to_refresh;
    }

    public String getLoading() {
        return loading;
    }

    public void setLoading(String loading) {
        this.loading = loading;
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        mRefreshListener = listener;
    }

    public void setOnLoadMoreListener(OnLoadMoreListener listener) {
        mLoadMoreListener = listener;
    }

    public void setOnPullUpListener(OnPullUpListener listener) {
        mPullUpListener = listener;
    }

    public void setOnPullDownListener(OnPullDownListener listener) {
        mPullDownListener = listener;
    }

    private int dp(float n) {

        return (int) TypedValue.applyDimension(1, n, dm);
    }

    public void setStateColor(int color) {
        mStateColor = color;
    }

    private void initView(Context context) {
        dm = context.getResources().getDisplayMetrics();

        mContext = context;
        TypedArray array = mContext.getTheme().obtainStyledAttributes(new int[]{
                android.R.attr.colorForeground,
                android.R.attr.colorBackground,
        });
        mForegroundColor = array.getColor(0, 0xFFFFFFFF);
        mBackgroundColor = array.getColor(1, 0xFF000000);
        array.recycle();
        setStateColor(mForegroundColor);

        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        refreshView = new HeadView(mContext);
        super.addView(refreshView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        pullableLayout = new FrameLayout(mContext);
        super.addView(pullableLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        loadmoreView = new FootView(mContext);
        super.addView(loadmoreView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        pullableLayout = (FrameLayout) getChildAt(1);
        initView();

        timer = new MyTimer(updateHandler);
        rotateAnimation = new RotateAnimation(0, 180, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setDuration(100);
        rotateAnimation.setRepeatCount(0);
        rotateAnimation.setFillAfter(true);

        refreshingAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        refreshingAnimation.setDuration(1500);
        refreshingAnimation.setRepeatCount(-1);
        refreshingAnimation.setFillAfter(true);

        // Tambahkan animasi putar berkecepatan konstan
        LinearInterpolator lir = new LinearInterpolator();
        rotateAnimation.setInterpolator(lir);
        refreshingAnimation.setInterpolator(lir);
    }

    private void hide() {
        timer.schedule(5);
    }

    /**
     * @param refreshResult PullToRefreshLayout.SUCCEEDberarti berhasil, PullToRefreshLayout.FAILberarti gagal
     */
    public void refreshFinish(int refreshResult) {
        if (state != REFRESHING)
            return;
        refreshingView.clearAnimation();
        refreshingView.setVisibility(View.GONE);
        switch (refreshResult) {
            case SUCCEED:
                // Berhasil disegarkan
                refreshStateImageView.setVisibility(View.VISIBLE);
                refreshStateTextView.setText(refresh_succeed);
                refreshStateImageView
                        .setBackgroundDrawable(new SucceedDrawable());
                break;
            case NOTHING:
                // Tidak ada pembaruan
                refreshStateImageView.setVisibility(View.VISIBLE);
                refreshStateTextView.setText(refresh_nothing);
                refreshStateImageView
                        .setBackgroundDrawable(new FailDrawable());
                break;
            case FAIL:
            default:
                // Gagal menyegarkan
                refreshStateImageView.setVisibility(View.VISIBLE);
                refreshStateTextView.setText(refresh_fail);
                refreshStateImageView
                        .setBackgroundDrawable(new FailDrawable());
                break;
        }
        if (pullDownY > 0) {
            // Hasil penyegaran ditampilkan 1 detik
            new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    changeState(DONE);
                    hide();
                }
            }.sendEmptyMessageDelayed(0, 1000);
        } else {
            changeState(DONE);
            hide();
        }
    }

    /**
     * Pemuatan selesai, tampilkan hasilnya. Catatan: metode ini wajib dipanggil setelah pemuatan selesai
     *
     * @param refreshResult PullToRefreshLayout.SUCCEEDberarti berhasil, PullToRefreshLayout.FAILberarti gagal
     */
    public void loadmoreFinish(int refreshResult) {
        if (state != LOADING)
            return;
        loadingView.clearAnimation();
        loadingView.setVisibility(View.GONE);
        switch (refreshResult) {
            case SUCCEED:
                // Berhasil dimuat
                loadStateImageView.setVisibility(View.VISIBLE);
                loadStateTextView.setText(load_succeed);
                loadStateImageView.setBackgroundDrawable(new SucceedDrawable());
                break;
            case NOTHING:
                // Tidak ada konten lagi
                loadStateImageView.setVisibility(View.VISIBLE);
                loadStateTextView.setText(load_nothing);
                loadStateImageView.setBackgroundDrawable(new FailDrawable());
                break;
            case FAIL:
            default:
                // Gagal memuat
                loadStateImageView.setVisibility(View.VISIBLE);
                loadStateTextView.setText(load_fail);
                loadStateImageView.setBackgroundDrawable(new FailDrawable());
                break;
        }
        if (pullUpY < 0) {
            // Hasil penyegaran ditampilkan 1 detik
            new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    changeState(DONE);
                    hide();
                }
            }.sendEmptyMessageDelayed(0, 1000);
        } else {
            changeState(DONE);
            hide();
        }
    }

    private void changeState(int to) {
        state = to;
        switch (state) {
            case INIT:
                // Status awal layout tarik-bawah
                refreshStateImageView.setVisibility(View.GONE);
                refreshStateTextView.setText(pull_to_refresh);
                pullView.clearAnimation();
                pullView.setVisibility(View.VISIBLE);
                // Status awal layout tarik-atas
                loadStateImageView.setVisibility(View.GONE);
                loadStateTextView.setText(pullup_to_load);
                pullUpView.clearAnimation();
                pullUpView.setVisibility(View.VISIBLE);
                break;
            case RELEASE_TO_REFRESH:
                // Status lepas untuk menyegarkan
                refreshStateTextView.setText(release_to_refresh);
                pullView.startAnimation(rotateAnimation);
                break;
            case REFRESHING:
                // Status sedang menyegarkan
                pullView.clearAnimation();
                refreshingView.setVisibility(View.VISIBLE);
                pullView.setVisibility(View.INVISIBLE);
                //refreshingView.startAnimation(refreshingAnimation);
                refreshStateTextView.setText(refreshing);
                break;
            case RELEASE_TO_LOAD:
                // Status lepas untuk memuat
                loadStateTextView.setText(release_to_load);
                pullUpView.startAnimation(rotateAnimation);
                break;
            case LOADING:
                // Status sedang memuat
                pullUpView.clearAnimation();
                loadingView.setVisibility(View.VISIBLE);
                pullUpView.setVisibility(View.INVISIBLE);
                //loadingView.startAnimation(refreshingAnimation);
                loadStateTextView.setText(loading);
                break;
            case DONE:
                // Penyegaran atau pemuatan selesai, tidak melakukan apa-apa
                break;
        }
    }

    /**
     * Tidak membatasi tarik atas maupun bawah
     */
    private void releasePull() {
        canPullDown = true;
        canPullUp = true;
    }

    /*
     * （non-Javadoc) Induk menentukan apakah event diteruskan, untuk mencegah konflik event
     *
     * @see android.view.ViewGroup#dispatchTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = ev.getY();
                lastY = downY;
                timer.cancel();
                mEvents = 0;
                releasePull();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                // Abaikan multi-sentuh
                mEvents = -1;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mEvents == 0) {
                    if (pullDownY > 0
                            || (canPullDown()
                            && canPullDown && state != LOADING)) {
                        // Boleh tarik ke bawah, tetapi tidak saat sedang memuat
                        // Perkecil jarak geser sebenarnya agar terasa seperti menarik dengan tenaga
                        pullDownY = pullDownY + (ev.getY() - lastY) / radio;
                        if (pullDownY < 0) {
                            pullDownY = 0;
                            canPullDown = false;
                            canPullUp = true;
                        }
                        if (pullDownY > getMeasuredHeight())
                            pullDownY = getMeasuredHeight();
                        if (state == REFRESHING) {
                            // Sentuh dan geser saat sedang menyegarkan
                            isTouch = true;
                        }
                    } else if (pullUpY < 0
                            || (canPullUp() && canPullUp && state != REFRESHING)) {
                        // Boleh tarik ke atas, tetapi tidak saat sedang menyegarkan
                        pullUpY = pullUpY + (ev.getY() - lastY) / radio;
                        if (pullUpY > 0) {
                            pullUpY = 0;
                            canPullDown = true;
                            canPullUp = false;
                        }
                        if (pullUpY < -getMeasuredHeight())
                            pullUpY = -getMeasuredHeight();
                        if (state == LOADING) {
                            // Sentuh dan geser saat sedang memuat
                            isTouch = true;
                        }
                    } else
                        releasePull();
                } else
                    mEvents = 0;
                lastY = ev.getY();
                // Ubah rasio sesuai jarak tarik
                radio = (float) (2 + 2 * Math.tan(Math.PI / 2 / getMeasuredHeight()
                        * (pullDownY + Math.abs(pullUpY))));
                if (pullDownY > 0 || pullUpY < 0)
                    requestLayout();
                if (pullDownY > 0) {
                    if (pullDownY <= refreshDist
                            && (state == RELEASE_TO_REFRESH || state == DONE)) {
                        // Jika jarak tarik belum mencapai jarak segarkan dan status saat ini adalah lepas untuk menyegarkan, ubah status menjadi tarik untuk menyegarkan
                        changeState(INIT);
                    }
                    if (pullDownY >= refreshDist && state == INIT) {
                        // Jika jarak tarik mencapai jarak segarkan dan status saat ini adalah status awal, ubah status menjadi lepas untuk menyegarkan
                        changeState(RELEASE_TO_REFRESH);
                    }
                } else if (pullUpY < 0) {
                    // Berikut penentuan tarik-atas untuk memuat, sama seperti di atas; perhatikan pullUpY bernilai negatif
                    if (-pullUpY <= loadmoreDist
                            && (state == RELEASE_TO_LOAD || state == DONE)) {
                        changeState(INIT);
                    }
                    // Operasi tarik ke atas
                    if (-pullUpY >= loadmoreDist && state == INIT) {
                        changeState(RELEASE_TO_LOAD);
                    }

                }
                // Karena operasi segarkan dan muat tidak bisa berjalan bersamaan, pullDownY dan pullUpY tidak akan sama-sama bukan 0, maka di sini dipakai (pullDownY +
                // Math.abs(pullUpY))sudah cukup tanpa membedakan status saat ini
                if ((pullDownY + Math.abs(pullUpY)) > 8) {
                    // Mencegah event tekan lama dan klik terpicu tidak sengaja saat menarik ke bawah
                    ev.setAction(MotionEvent.ACTION_CANCEL);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (pullDownY > refreshDist || -pullUpY > loadmoreDist) {
                    isTouch = false;
                }
                if (state == RELEASE_TO_REFRESH) {
                    changeState(REFRESHING);
                    // operasi segarkan
                    if (mRefreshListener != null)
                        mRefreshListener.onRefresh(this);
                } else if (state == RELEASE_TO_LOAD) {
                    changeState(LOADING);
                    // operasi muat
                    if (mLoadMoreListener != null)
                        mLoadMoreListener.onLoadMore(this);
                }
                hide();
                break;
            default:
                break;
        }
        // Distribusi event diserahkan ke kelas induk
        super.dispatchTouchEvent(ev);
        return true;
    }

    public void setPullUpEnabled(boolean canPullUp) {

        mPullUp = canPullUp;
    }

    private boolean canPullUp() {

        if (!mPullUp || pullableView == null)
            return false;
        if (mPullUpListener != null)
            return mPullUpListener.onPullUp(pullableView);

        if (pullableView instanceof ListView)
            return listViewCanPullUp((ListView) pullableView);
        if (pullableView instanceof GridView)
            return gridViewCanPullUp((GridView) pullableView);
        if (pullableView instanceof ExpandableListView)
            return expandableListViewCanPullUp((ExpandableListView) pullableView);
        if (pullableView instanceof ScrollView)
            return scrollViewCanPullUp((ScrollView) pullableView);
        if (pullableView instanceof WebView)
            return webViewCanPullUp((WebView) pullableView);

        return true;
    }

    private boolean expandableListViewCanPullUp(ExpandableListView view) {

        if (view.getCount() == 0) {
            // Bisa tarik ke atas untuk memuat meski tidak ada item
            return true;
        } else if (view.getLastVisiblePosition() == (view.getCount() - 1)) {
            // Sudah sampai di bawah
            if (view.getChildAt(view.getLastVisiblePosition() - view.getFirstVisiblePosition()) != null
                    && view.getChildAt(
                    view.getLastVisiblePosition()
                            - view.getFirstVisiblePosition()).getBottom() <= view.getMeasuredHeight())
                return true;
        }
        return false;
    }

    private boolean gridViewCanPullUp(GridView view) {

        if (view.getCount() == 0) {
            // Bisa tarik ke atas untuk memuat meski tidak ada item
            return true;
        } else if (view.getLastVisiblePosition() == (view.getCount() - 1)) {
            // Sudah sampai di bawah
            if (view.getChildAt(view.getLastVisiblePosition() - view.getFirstVisiblePosition()) != null
                    && view.getChildAt(
                    view.getLastVisiblePosition()
                            - view.getFirstVisiblePosition()).getBottom() <= view.getMeasuredHeight())
                return true;
        }
        return false;

    }

    private boolean scrollViewCanPullUp(ScrollView view) {

        if (view.getScrollY() >= (view.getChildAt(0).getHeight() - view.getMeasuredHeight()))
            return true;
        else
            return false;
    }

    private boolean webViewCanPullUp(WebView view) {

        if (view.getScrollY() >= view.getContentHeight() * view.getScale()
                - view.getMeasuredHeight())
            return true;
        else
            return false;

    }

    private boolean listViewCanPullUp(ListView view) {

        if (view.getCount() == 0) {
            // Bisa tarik ke atas untuk memuat meski tidak ada item
            return true;
        } else if (view.getLastVisiblePosition() == (view.getCount() - 1)) {
            // Sudah sampai di bawah
            if (view.getChildAt(view.getLastVisiblePosition() - view.getFirstVisiblePosition()) != null
                    && view.getChildAt(
                    view.getLastVisiblePosition()
                            - view.getFirstVisiblePosition()).getBottom() <= view.getMeasuredHeight())
                return true;
        }
        return false;
    }

    public void setPullDownEnabled(boolean canPullDown) {

        mPullDown = canPullDown;
    }

    private boolean canPullDown() {

        if (mPullDown == false || pullableView == null)
            return false;
        if (mPullDownListener != null)
            return mPullDownListener.onPullDown(pullableView);

        if (pullableView instanceof AbsListView)
            return absListViewCanPullDown((AbsListView) pullableView);
        if (pullableView instanceof ScrollView)
            return viewCanPullDown((ScrollView) pullableView);
        if (pullableView instanceof WebView)
            return viewCanPullDown((WebView) pullableView);

        return true;
    }

    private boolean viewCanPullDown(View view) {

        if (view.getScrollY() == 0)
            return true;
        else
            return false;

    }

    private boolean absListViewCanPullDown(AbsListView view) {

        if (view.getCount() == 0) {
            // Bisa tarik ke bawah untuk menyegarkan meski tidak ada item
            return true;
        } else if (view.getFirstVisiblePosition() == 0
                && view.getChildAt(0).getTop() >= 0) {
            // Sudah sampai di atas ListView
            return true;
        } else
            return false;

    }

    /**
     * Segarkan otomatis
     */
    public void autoRefresh() {
        AutoRefreshAndLoadTask task = new AutoRefreshAndLoadTask();
        task.execute(8);
    }

    /**
     * Muat otomatis
     */
    public void autoLoad() {
        pullUpY = -loadmoreDist;
        requestLayout();
        changeState(LOADING);
        // operasi muat
        if (mLoadMoreListener != null)
            mLoadMoreListener.onLoadMore(this);
    }

    private void initView() {
        // Inisialisasi layout tarik-bawah
        pullView = refreshView.getPullView();
        refreshStateTextView = refreshView.getStateText();
        refreshingView = refreshView.getLoadingView();
        refreshStateImageView = refreshView.getStateView();
        // Inisialisasi layout tarik-atas
        pullUpView = loadmoreView.getPullView();
        loadStateTextView = loadmoreView.getStateText();
        loadingView = loadmoreView.getLoadingView();
        loadStateImageView = loadmoreView.getStateView();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!isLayout) {
            // Inisialisasi dilakukan di sini saat pertama kali masuk
            isLayout = true;
            refreshDist = ((ViewGroup) refreshView).getChildAt(0)
                    .getMeasuredHeight();
            loadmoreDist = ((ViewGroup) loadmoreView).getChildAt(0)
                    .getMeasuredHeight();
            loadStateTextView.setTextColor(mStateColor);
            refreshStateTextView.setTextColor(mStateColor);
        }
        // Ubah layout subview; di sini (pullDownY + pullUpY) langsung dipakai sebagai offset sehingga status saat ini tidak perlu dibedakan
        refreshView.layout(0,
                (int) (pullDownY + pullUpY) - refreshView.getMeasuredHeight(),
                refreshView.getMeasuredWidth(), (int) (pullDownY + pullUpY));
        pullableLayout.layout(0, (int) (pullDownY + pullUpY),
                pullableLayout.getMeasuredWidth(), (int) (pullDownY + pullUpY)
                        + pullableLayout.getMeasuredHeight());
        loadmoreView.layout(0,
                (int) (pullDownY + pullUpY) + pullableLayout.getMeasuredHeight(),
                loadmoreView.getMeasuredWidth(),
                (int) (pullDownY + pullUpY) + pullableLayout.getMeasuredHeight()
                        + loadmoreView.getMeasuredHeight());
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {

        child.setLayoutParams(new FrameLayout.LayoutParams(params.width, params.height));
        pullableView = child;
        pullableLayout.addView(child);
    }

    @Override
    public void addView(View child) {

        pullableView = child;
        pullableLayout.addView(child);
    }

    /**
     * Antarmuka callback penyegaran
     *
     * @author chenjing
     */
    public interface OnRefreshListener {
        /**
         * operasi segarkan
         */
        void onRefresh(PullingLayout pullToRefreshLayout);
    }

    /**
     * Antarmuka callback pemuatan
     *
     * @author chenjing
     */
    public interface OnLoadMoreListener {

        /**
         * operasi muat
         */
        void onLoadMore(PullingLayout pullToRefreshLayout);
    }

    public interface OnPullUpListener {
        public boolean onPullUp(View view);
    }

    public interface OnPullDownListener {
        public boolean onPullDown(View view);
    }

    /**
     * @author chenjing Task yang mensimulasikan gerakan jari secara otomatis
     */
    private class AutoRefreshAndLoadTask extends
            AsyncTask<Integer, Float, String> {
        private int MOVE_SPEED=16;
        @Override
        protected String doInBackground(Integer... params) {
            while (pullDownY < 4 / 3 * refreshDist) {
                pullDownY += MOVE_SPEED;
                publishProgress(pullDownY);
                try {
                    Thread.sleep(params[0]);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            changeState(REFRESHING);
            // operasi segarkan
            if (mRefreshListener != null)
                mRefreshListener.onRefresh(PullingLayout.this);
            hide();
        }

        @Override
        protected void onProgressUpdate(Float... values) {
            if (pullDownY > refreshDist)
                changeState(RELEASE_TO_REFRESH);
            requestLayout();
        }

    }

    class MyTimer {
        private Handler handler;
        private Timer timer;
        private MyTask mTask;

        public MyTimer(Handler handler) {
            this.handler = handler;
            timer = new Timer();
        }

        public void schedule(long period) {
            if (mTask != null) {
                mTask.cancel();
                mTask = null;
            }
            mTask = new MyTask(handler);
            timer.schedule(mTask, 0, period);
        }

        public void cancel() {
            if (mTask != null) {
                mTask.cancel();
                mTask = null;
            }
        }

        class MyTask extends TimerTask {
            private Handler handler;

            public MyTask(Handler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                handler.obtainMessage().sendToTarget();
            }

        }
    }

    public class HeadView extends RelativeLayout {

        private ImageView mLoadingView;
        private TextView mStateText;
        private ImageView mStateView;
        private ImageView mPullView;

        public HeadView(Context context) {
            super(context);
            initView(context);
        }


        public HeadView(Context context, AttributeSet attrs) {
            super(context, attrs);
            initView(context);
        }

        public HeadView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
            initView(context);
        }

        public ImageView getStateView() {
            return mStateView;
        }

        public TextView getStateText() {
            return mStateText;
        }

        public ImageView getLoadingView() {
            return mLoadingView;
        }

        public ImageView getPullView() {
            return mPullView;
        }

        private void initView(Context context) {

            int w = dp(30);

            RelativeLayout layout = new RelativeLayout(context);
            layout.setPadding(0, dp(20), 0, dp(20));
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.addRule(12);
            addView(layout, lp);

            RelativeLayout layout2 = new RelativeLayout(context);
            LayoutParams lp2 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp2.addRule(13);
            layout.addView(layout2, lp2);

            mPullView = new ImageView(context);
            mPullView.setBackgroundDrawable(new PullDownDrawable());
            LayoutParams lp3 = new LayoutParams(w, w);
            lp3.setMargins(dp(60), 0, 0, 0);
            lp3.addRule(15);
            layout2.addView(mPullView, lp3);

            mLoadingView = new ImageView(context);
            mLoadingView.setVisibility(GONE);
            mLoadingView.setBackgroundDrawable(new LoadingDrawable());
            LayoutParams lp4 = new LayoutParams(w, w);
            lp4.setMargins(dp(60), 0, 0, 0);
            lp4.addRule(15);
            layout2.addView(mLoadingView, lp4);

            mStateText = new TextView(context);
            mStateText.setText(pull_to_refresh);
            mStateText.setTextSize(16);
            LayoutParams lp5 = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp5.addRule(13);
            layout2.addView(mStateText, lp5);

            mStateView = new ImageView(context);
            mStateView.setVisibility(GONE);
            LayoutParams lp6 = new LayoutParams(w, w);
            lp6.setMargins(dp(60), 0, 0, 0);
            lp6.addRule(15);
            layout2.addView(mStateView, lp6);
        }

    }

    public class FootView extends RelativeLayout {

        private ImageView mLoadingView;
        private TextView mStateText;
        private ImageView mStateView;
        private ImageView mPullView;

        public FootView(Context context) {
            super(context);
            initView(context);
        }

        public FootView(Context context, AttributeSet attrs) {
            super(context, attrs);
            initView(context);
        }

        public FootView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
            initView(context);
        }

        public ImageView getStateView() {
            return mStateView;
        }

        public TextView getStateText() {
            return mStateText;
        }

        public ImageView getLoadingView() {
            return mLoadingView;
        }

        public ImageView getPullView() {
            return mPullView;
        }

        private void initView(Context context) {

            int w = dp(30);
            RelativeLayout layout = new RelativeLayout(context);
            layout.setPadding(0, dp(20), 0, dp(20));
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.addRule(10);
            addView(layout, lp);

            RelativeLayout layout2 = new RelativeLayout(context);
            LayoutParams lp2 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp2.addRule(13);
            layout.addView(layout2, lp2);

            mPullView = new ImageView(context);
            mPullView.setBackgroundDrawable(new PullUpDrawable());
            LayoutParams lp3 = new LayoutParams(w, w);
            lp3.setMargins(dp(60), 0, 0, 0);
            lp3.addRule(15);
            layout2.addView(mPullView, lp3);

            mLoadingView = new ImageView(context);
            mLoadingView.setVisibility(GONE);
            mLoadingView.setBackgroundDrawable(new LoadingDrawable());
            LayoutParams lp4 = new LayoutParams(w, w);
            lp4.setMargins(dp(60), 0, 0, 0);
            lp4.addRule(15);
            layout2.addView(mLoadingView, lp4);

            mStateText = new TextView(context);
            mStateText.setText(pullup_to_load);
            mStateText.setTextSize(16);
            LayoutParams lp5 = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp5.addRule(13);
            layout2.addView(mStateText, lp5);

            mStateView = new ImageView(context);
            mStateView.setVisibility(GONE);
            LayoutParams lp6 = new LayoutParams(w, w);
            lp6.setMargins(dp(60), 0, 0, 0);
            lp6.addRule(15);
            layout2.addView(mStateView, lp6);

        }

    }

    private class PullDownDrawable extends Drawable {

        private Paint p;

        PullDownDrawable() {
            p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setAntiAlias(true);
            p.setStrokeWidth(dp(2));
            //p.setShadowLayer(dp(2),dp(2),dp(2), Color.GRAY);
        }

        @Override
        public void draw(Canvas c) {

            p.setColor(mStateColor);
            Rect b = getBounds();
            int r = (int) ((float) Math.min(b.right, b.bottom) * 0.35);
            c.drawCircle(b.right / 2, b.bottom / 2, r, p);
            Path path = new Path();
            path.moveTo(b.right * 0.5f, b.bottom * 0.25f);
            path.lineTo(b.right * 0.5f, b.bottom * 0.75f);
            path.moveTo(b.right * 0.25f, b.bottom * 0.5f);
            path.lineTo(b.right * 0.5f, b.bottom * 0.75f);
            path.lineTo(b.right * 0.75f, b.bottom * 0.5f);
            c.drawPath(path, p);
            //c.drawLine(b.right / 2, b.bottom / 4, b.right / 2, b.bottom / 4 * 3, p);
            //c.drawLine(b.right / 4, b.bottom / 2, b.right / 2, b.bottom / 4 * 3, p);
            //c.drawLine(b.right / 4 * 3, b.bottom / 2, b.right / 2, b.bottom / 4 * 3, p);
        }

        @Override
        public void setAlpha(int p1) {

            p.setAlpha(p1);
        }

        @Override
        public void setColorFilter(ColorFilter p1) {

            p.setColorFilter(p1);
        }

        @Override
        public int getOpacity() {

            return PixelFormat.UNKNOWN;
        }
    }

    private class PullUpDrawable extends Drawable {

        private Paint p;

        PullUpDrawable() {
            p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setAntiAlias(true);
            p.setStrokeWidth(dp(2));
            //p.setShadowLayer(dp(2),dp(2),dp(2), Color.GRAY);
        }

        @Override
        public void draw(Canvas c) {

            p.setColor(mStateColor);
            Rect b = getBounds();
            int r = (int) ((float) Math.min(b.right, b.bottom) * 0.35);
            c.drawCircle(b.right / 2, b.bottom / 2, r, p);
            Path path = new Path();
            path.moveTo(b.right * 0.5f, b.bottom * 0.25f);
            path.lineTo(b.right * 0.5f, b.bottom * 0.75f);
            path.moveTo(b.right * 0.25f, b.bottom * 0.5f);
            path.lineTo(b.right * 0.5f, b.bottom * 0.25f);
            path.lineTo(b.right * 0.75f, b.bottom * 0.5f);
            c.drawPath(path, p);
            //c.drawLine(b.right / 2, b.bottom / 4, b.right / 2, b.bottom / 4 * 3, p);
            //c.drawLine(b.right / 4, b.bottom / 2, b.right / 2, b.bottom / 4, p);
            //c.drawLine(b.right / 4 * 3, b.bottom / 2, b.right / 2, b.bottom / 4, p);
        }

        @Override
        public void setAlpha(int p1) {

            p.setAlpha(p1);
        }

        @Override
        public void setColorFilter(ColorFilter p1) {

            p.setColorFilter(p1);
        }

        @Override
        public int getOpacity() {

            return PixelFormat.UNKNOWN;
        }
    }

    private class LoadingDrawable2 extends Drawable {

        private Paint p;
        private int n = 0;
        private int m = 0;
        private int sn = 6;
        private int sm = 2;

        LoadingDrawable2() {
            p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setAntiAlias(true);
            p.setStrokeWidth(dp(2));
            //p.setShadowLayer(dp(2),dp(2),dp(2), Color.GRAY);
        }

        @Override
        public void draw(Canvas c) {

            p.setColor(mStateColor);
            Rect b = getBounds();
            int r = (int) ((float) Math.min(b.right, b.bottom) * 0.35);
            RectF f = new RectF(r / 2, r / 2, r * 2.5f, r * 2.5f);
            if (n > 360) {
                sm += sn;
                sn = 0 - sn;
            } else if (n < 6) {
                sn = 6;
                sm = 2;
            }
            n += sn;
            m += sm;
            c.drawArc(f, m % 360, n, false, p);
            invalidateSelf();
            /*float f = (float) r * 1.5f;
            SweepGradient mShader = new SweepGradient(f, f, mBackgroundColor, mStateColor);
            p.setShader(mShader);
            DashPathEffect effects = new DashPathEffect(new float[]{r / 5, r / 6}, 1);
            p.setPathEffect(effects);
            c.drawCircle(b.right / 2, b.bottom / 2, r, p);*/
        }

        @Override
        public void setAlpha(int p1) {

            p.setAlpha(p1);
        }

        @Override
        public void setColorFilter(ColorFilter p1) {

            p.setColorFilter(p1);
        }

        @Override
        public int getOpacity() {

            return PixelFormat.UNKNOWN;
        }
    }


    private class SucceedDrawable2 extends Drawable {

        private Paint p;

        SucceedDrawable2() {
            p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setAntiAlias(true);
            p.setStrokeWidth(dp(2));
            //p.setShadowLayer(dp(2),dp(2),dp(2), Color.GRAY);
        }

        @Override
        public void draw(Canvas c) {

            p.setColor(mStateColor);
            Rect b = getBounds();
            int r = (int) ((float) Math.min(b.right, b.bottom) * 0.35);
            c.drawCircle(b.right / 2, b.bottom / 2, r, p);
            Path path = new Path();
            path.moveTo(b.right * 0.3f, b.bottom * 0.5f);
            path.lineTo(b.right * 0.45f, b.bottom * 0.7f);
            path.lineTo(b.right * 0.75f, b.bottom * 0.4f);
            c.drawPath(path, p);
            //c.drawLine(b.right * 0.3f, b.bottom / 2, b.right * 0.45f, b.bottom * 0.7f, p);
            //c.drawLine(b.right / 4 * 3, b.bottom * 0.4f, b.right * 0.45f, b.bottom * 0.7f, p);
        }

        @Override
        public void setAlpha(int p1) {

            p.setAlpha(p1);
        }

        @Override
        public void setColorFilter(ColorFilter p1) {

            p.setColorFilter(p1);
        }

        @Override
        public int getOpacity() {

            return PixelFormat.UNKNOWN;
        }
    }

    private class FailDrawable2 extends Drawable {

        private Paint p;

        FailDrawable2() {
            p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setAntiAlias(true);
            p.setStrokeWidth(dp(2));
            //p.setShadowLayer(dp(2),dp(2),dp(2), Color.GRAY);
        }

        @Override
        public void draw(Canvas c) {

            p.setColor(mStateColor);
            Rect b = getBounds();
            int r = (int) ((float) Math.min(b.right, b.bottom) * 0.35);
            c.drawCircle(b.right / 2, b.bottom / 2, r, p);
            c.drawLine(b.right / 2, b.bottom * 0.25f, b.right / 2, b.bottom * 0.65f, p);
            c.drawLine(b.right / 2, b.bottom * 0.7f, b.right / 2, b.bottom * 0.75f, p);
        }

        @Override
        public void setAlpha(int p1) {
            p.setAlpha(p1);
        }

        @Override
        public void setColorFilter(ColorFilter p1) {

            p.setColorFilter(p1);
        }

        @Override
        public int getOpacity() {

            return PixelFormat.UNKNOWN;
        }
    }

    private int n = 0;
    private int m = 0;
    private int x = 0;
    private int y = 0;
    private int sn = 6;
    private int sm = 2;

    private class LoadingDrawable extends Drawable {

        static final int STATE_LOADING = 0;
        static final int STATE_SUCCESS = 1;
        static final int STATE_FAIL = -1;
        private Paint p;
        private int mState;

        LoadingDrawable() {
            p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setAntiAlias(true);
            p.setStrokeWidth(dp(2));
        }

        void setState(int state) {
            mState = state;
        }

        void loading() {
            reset();
        }

        void succe() {
            mState = STATE_SUCCESS;
        }

        void fail() {
            mState = STATE_FAIL;
        }

        void reset() {
            mState = STATE_LOADING;
            sn = 6;
            sm = 2;
            n = 0;
            m = 0;
            x = 0;
            y = 0;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas c) {

            p.setColor(mStateColor);
            Rect b = getBounds();
            int r = (int) ((float) Math.min(b.right, b.bottom));
            RectF f = new RectF(r * 0.15f, r * 0.15f, r * 0.85f, r * 0.85f);
            if (n >= 360 && mState == STATE_LOADING) {
                sm = 8;
                sn = 0 - 6;
            } else if (n <= 6) {
                sn = 6;
                sm = 2;
            }
            if (n < 360 || mState == STATE_LOADING) {
                if (mState == STATE_LOADING) {
                    n += sn;
                    m += sm;
                    m %= 360;
                } else {
                    n += sn * 2;
                    m += sm * 2;
                    m %= 360;
                }
            }
            c.drawArc(f, m, n, false, p);

            if (n >= 360) {
                sn = -6;
                sm = 8;

                if (mState == STATE_SUCCESS) {
                    Path path = new Path();
                    path.moveTo(b.right * 0.3f, b.bottom * 0.5f);
                    path.lineTo(b.right * 0.45f, b.bottom * 0.7f);
                    path.lineTo(b.right * 0.75f, b.bottom * 0.4f);
                    c.drawPath(path, p);
                } else if (mState == STATE_FAIL) {
                    c.drawLine(b.right / 2, b.bottom * 0.25f, b.right / 2, b.bottom * 0.65f, p);
                    c.drawLine(b.right / 2, b.bottom * 0.7f, b.right / 2, b.bottom * 0.75f, p);
                }
            }
            invalidateSelf();
        }

        @Override
        public void setAlpha(int p1) {

            p.setAlpha(p1);
        }

        @Override
        public void setColorFilter(ColorFilter p1) {

            p.setColorFilter(p1);
        }

        @Override
        public int getOpacity() {

            return PixelFormat.UNKNOWN;
        }
    }

    private class SucceedDrawable extends LoadingDrawable {

        SucceedDrawable() {
            super();
            succe();
        }

    }

    private class FailDrawable extends LoadingDrawable {

        FailDrawable() {
            super();
            fail();
        }
    }
}

