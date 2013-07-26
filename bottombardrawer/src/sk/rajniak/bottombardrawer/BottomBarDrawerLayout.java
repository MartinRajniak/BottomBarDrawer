package sk.rajniak.bottombardrawer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.KeyEventCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.widget.DrawerLayout.SimpleDrawerListener;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

/**
 * BottomBarDrawerLayout acts as a top-level container for window content that allows for
 * interactive "drawer" view to be pulled out from the bottom edge of the window. This version
 * of drawer is working with always visible part of the drawer that serves as bar that when
 * dragged or touched performs open/close operation.
 *
 * <p>BottomBarDrawerLayout allows only two children from which on is content and another one is drawer.
 * We differentiate between the two by allowing user to mark the content view by setting its id 
 * to <code>android:id="@android:id/content"</code>. </p>
 *
 * <p>Another required layout attribute for the drawer to work is <code>bottomBarHeight</code>. 
 * This attribute tells the layout the height of the bottom bar that is used for opening the drawer. 
 * It is advisable to set this parameter at least to the usual value used 
 * for click-able areas: <code>48dp</code></p>
 *
 * <p>{@link DrawerListener} can be used to monitor the state and motion of drawer view.
 * Avoid performing expensive operations such as layout during animation as it can cause
 * stuttering; try to perform expensive operations during the {@link #STATE_IDLE} state.
 * {@link SimpleDrawerListener} offers default/no-op implementations of each callback method.</p>
 *
 */
public class BottomBarDrawerLayout extends ViewGroup {

	/**
	 * Indicates that the drawer is in an idle, settled state. No animation is
	 * in progress.
	 */
	public static final int STATE_IDLE = ViewDragHelper.STATE_IDLE;

	/**
	 * Indicates that the drawer is currently being dragged by the user.
	 */
	public static final int STATE_DRAGGING = ViewDragHelper.STATE_DRAGGING;

	/**
	 * Indicates that the drawer is in the process of settling to a final
	 * position.
	 */
	public static final int STATE_SETTLING = ViewDragHelper.STATE_SETTLING;

	/**
	 * Minimum velocity that will be detected as a fling
	 */
	private static final int MIN_FLING_VELOCITY = 400; // dips per second
	
	private static final int DEFAULT_CONTENT_SCRIM_COLOR = 0x99000000;
	private static final int DEFAULT_DRAWER_SCRIM_COLOR = 0x99FFFFFF;

	private int mContentScrimColor = DEFAULT_CONTENT_SCRIM_COLOR;
	private float mContentScrimOpacity;
	private final Paint mContentScrimPaint = new Paint();

	private int mDrawerScrimColor = DEFAULT_DRAWER_SCRIM_COLOR;
	private float mDrawerScrimOpacity;
	private final Paint mDrawerScrimPaint = new Paint();

	private final ViewDragHelper mDragger;
	private final ViewDragCallback mDragCallback;
	private int mDrawerState;
	private boolean mInLayout;
	private boolean mFirstLayout = true;

	private final int mVisiblePartHeight;
	private boolean mAlwaysInTapRegion;
	private final int mTouchSlopSquare;

	private float mInitialMotionX;
	private float mInitialMotionY;

	private Drawable mShadow;

	private DrawerListener mListener;

	private boolean mBottomBarTouched;

	/**
	 * Listener for monitoring events about drawer.
	 */
	public interface DrawerListener {
		/**
		 * Called when a drawer's position changes.
		 * 
		 * @param drawerView
		 *            The child view that was moved
		 * @param slideOffset
		 *            The new offset of this drawer within its range, from 0-1
		 */
		public void onDrawerSlide(View drawerView, float slideOffset);

		/**
		 * Called when a drawer has settled in a completely open state. The
		 * drawer is interactive at this point.
		 * 
		 * @param drawerView
		 *            Drawer view that is now open
		 */
		public void onDrawerOpened(View drawerView);

		/**
		 * Called when a drawer has settled in a completely closed state.
		 * 
		 * @param drawerView
		 *            Drawer view that is now closed
		 */
		public void onDrawerClosed(View drawerView);

		/**
		 * Called when the drawer motion state changes. The new state will be
		 * one of {@link #STATE_IDLE}, {@link #STATE_DRAGGING} or
		 * {@link #STATE_SETTLING}.
		 * 
		 * @param newState
		 *            The new drawer motion state
		 */
		public void onDrawerStateChanged(int newState);
	}

	public BottomBarDrawerLayout(Context context) {
		this(context, null);
	}

	public BottomBarDrawerLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public BottomBarDrawerLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		final float density = getResources().getDisplayMetrics().density;
		final float minVel = MIN_FLING_VELOCITY * density;

		final TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.BottomBarDrawerLayout, defStyle, 0);
		mVisiblePartHeight = typedArray.getDimensionPixelSize(R.styleable.BottomBarDrawerLayout_bottomBarHeight, -1);
		typedArray.recycle();

		mDragCallback = new ViewDragCallback();

		mDragger = ViewDragHelper.create(this, 0.5f, mDragCallback);
		mDragger.setEdgeTrackingEnabled(ViewDragHelper.DIRECTION_VERTICAL);
		mDragger.setMinVelocity(minVel);
		mDragCallback.setDragger(mDragger);
		
		final ViewConfiguration configuration = ViewConfiguration.get(context);
		final int touchSlop = configuration.getScaledTouchSlop();
		mTouchSlopSquare = touchSlop * touchSlop;

		// So that we can catch the back button
		setFocusableInTouchMode(true);

		ViewCompat.setAccessibilityDelegate(this, new AccessibilityDelegate());
	}

	/**
	 * Set a simple drawable used for the left or right shadow. The drawable
	 * provided must have a nonzero intrinsic width.
	 * 
	 * @param shadowDrawable
	 *            Shadow drawable to use at the edge of a drawer
	 */
	public void setDrawerShadow(Drawable shadowDrawable) {
		/*
		 * TODO Someone someday might want to set more complex drawables here.
		 * They're probably nuts, but we might want to consider registering
		 * callbacks, setting states, etc. properly.
		 */
		mShadow = shadowDrawable;
		invalidate();
	}
	
	/**
	 * Set a simple drawable used for the left or right shadow. The drawable
	 * provided must have a nonzero intrinsic width.
	 * 
	 * @param resId
	 *            Resource id of a shadow drawable to use at the edge of a
	 *            drawer
	 */
	public void setDrawerShadow(int resId) {
		setDrawerShadow(getResources().getDrawable(resId));
	}
	
	/**
	 * Set a color to use for the scrim that obscures primary content while a
	 * drawer is open.
	 * 
	 * @param color
	 *            Color to use in 0xAARRGGBB format.
	 */
	public void setContentScrimColor(int color) {
		mContentScrimColor = color;
		invalidate();
	}

	/**
	 * Set a color to use for the scrim that obscures drawer content while a
	 * drawer is opening.
	 * 
	 * @param color
	 *            Color to use in 0xAARRGGBB format.
	 */
	public void setDrawerScrimColor(int color) {
		mDrawerScrimColor = color;
		invalidate();
	}
	
	/**
	 * Set a listener to be notified of drawer events.
	 * 
	 * @param listener
	 *            Listener to notify when drawer events occur
	 * @see DrawerListener
	 */
	public void setDrawerListener(DrawerListener listener) {
		mListener = listener;
	}
	
	/**
	 * Check if the drawer view is currently visible on-screen. The drawer
	 * may be dragged or fully extended.
	 * 
	 * @return true if the drawer is visible on-screen
	 * @see #isDrawerOpen()
	 */
	public boolean isDrawerVisible(){
		final View drawer = findDrawer();
		if(drawer != null && isDrawerVisible(drawer)){
			return true;
		}
		return false;
	}
	
	private boolean isDrawerVisible(View drawer) {
		if (isContentView(drawer)) {
			throw new IllegalArgumentException("View " + drawer + " is not a drawer");
		}
		return ((LayoutParams) drawer.getLayoutParams()).onScreen > 0;
	}

    /**
     * Check if the drawer view is currently in an open state.
     * To be considered "open" the drawer must have settled into its fully
     * visible state. To check for partial visibility use
     * {@link #isDrawerVisible()}.
     *
     * @return true if the drawer view is in an open state
     * @see #isDrawerVisible()
     */
	public boolean isDrawerOpen(){
		final View drawer = findDrawer();
		if(drawer != null && isDrawerOpen(drawer)){
			return true;
		}
		return false;
	}
	
    private boolean isDrawerOpen(View drawer) {
        if (isContentView(drawer)) {
            throw new IllegalArgumentException("View " + drawer + " is not a drawer");
        }
        return ((LayoutParams) drawer.getLayoutParams()).knownOpen;
    }

	/**
 	 * Should be called whenever a ViewDragHelper's state changes.
	 */
	void updateDrawerState(int activeState, View activeDrawer) {
		final int state = mDragger.getViewDragState();

		if (activeDrawer != null && activeState == STATE_IDLE) {
			final LayoutParams lp = (LayoutParams) activeDrawer.getLayoutParams();
			if (lp.onScreen == 0) {
				dispatchOnDrawerClosed(activeDrawer);
			} else if (lp.onScreen == 1) {
				dispatchOnDrawerOpened(activeDrawer);
			}
		}

		if (state != mDrawerState) {
			mDrawerState = state;

			if (mListener != null) {
				mListener.onDrawerStateChanged(state);
			}
		}
	}
	
	void dispatchOnDrawerClosed(View drawerView) {
		final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
		if (lp.knownOpen) {
			lp.knownOpen = false;
			if (mListener != null) {
				mListener.onDrawerClosed(drawerView);
			}
			sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
		}
	}

	void dispatchOnDrawerOpened(View drawerView) {
		final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
		if (!lp.knownOpen) {
			lp.knownOpen = true;
			if (mListener != null) {
				mListener.onDrawerOpened(drawerView);
			}
			drawerView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
		}
	}
	
	void dispatchOnDrawerSlide(View drawerView, float slideOffset) {
		if (mListener != null) {
			mListener.onDrawerSlide(drawerView, slideOffset);
		}
	}
	
	void setDrawerViewOffset(View drawerView, float slideOffset) {
		final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
		if (slideOffset == lp.onScreen) {
			return;
		}

		lp.onScreen = slideOffset;
		dispatchOnDrawerSlide(drawerView, slideOffset);
	}

	float getDrawerViewOffset(View drawerView) {
		return ((LayoutParams) drawerView.getLayoutParams()).onScreen;
	}
	
	View findOpenDrawer() {
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			if (((LayoutParams) child.getLayoutParams()).knownOpen) {
				return child;
			}
		}
		return null;
	}
	
	View findDrawer() {
		for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
			final View child = getChildAt(i);
			if (!isContentView(child)) {
				return child;
			}
		}
		return null;
	}

	View findContent() {
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			if (isContentView(child)) {
				return child;
			}
		}
		return null;
	}
	
	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		mFirstLayout = true;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		mFirstLayout = true;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY || heightMode != MeasureSpec.EXACTLY) {
            throw new IllegalArgumentException(
                    "BottomBarDrawerLayout must be measured with MeasureSpec.EXACTLY.");
        }
		
        setMeasuredDimension(widthSize, heightSize);
        
        // Gravity value for each drawer we've seen. Only one of each permitted.
        for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (isContentView(child)) {
                // Content views get measured at exactly the layout's size.
                final int contentWidthSpec = MeasureSpec.makeMeasureSpec(
                        widthSize - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
                final int contentHeightSpec = MeasureSpec.makeMeasureSpec(
                        heightSize - lp.topMargin - lp.bottomMargin - mVisiblePartHeight, MeasureSpec.EXACTLY);
                child.measure(contentWidthSpec, contentHeightSpec);
            } else {
                final int drawerWidthSpec = getChildMeasureSpec(widthMeasureSpec,
                        lp.leftMargin + lp.rightMargin,
                        lp.width);
                final int drawerHeightSpec = getChildMeasureSpec(heightMeasureSpec,
                        lp.topMargin + lp.bottomMargin,
                        lp.height);
                child.measure(drawerWidthSpec, drawerHeightSpec);
            } 
        }
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		mInLayout = true;
		for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
			final View child = getChildAt(i);

			final LayoutParams lp = (LayoutParams) child.getLayoutParams();

			if (isContentView(child)) {
				child.layout(lp.leftMargin, lp.topMargin, lp.leftMargin + child.getMeasuredWidth(), lp.topMargin + child.getMeasuredHeight());

				final View drawerView = findDrawer();
				if(drawerView != null){
					final LayoutParams drawerLayoutParams = (LayoutParams) drawerView.getLayoutParams();
					if(drawerLayoutParams.onScreen == 1.f
							&& drawerView.getMeasuredHeight() == getMeasuredHeight()){
						child.setVisibility(INVISIBLE);
					}
				}
			} else if (lp.onScreen == 0) { // Drawer view - hidden
				final int top = getMeasuredHeight() - mVisiblePartHeight;

				child.layout(lp.leftMargin, top, lp.leftMargin + child.getMeasuredWidth(), top + child.getMeasuredHeight());
			} else { // Drawer view - displayed
				final int top = getMeasuredHeight() - mVisiblePartHeight - (int) ((child.getMeasuredHeight() - mVisiblePartHeight) * lp.onScreen);

				child.layout(lp.leftMargin, top, lp.leftMargin + child.getMeasuredWidth(), top + child.getMeasuredHeight());
			}
		}
		mInLayout = false;
		mFirstLayout = false;
	}

	@Override
	public void requestLayout() {
		if (!mInLayout) {
			super.requestLayout();
		}
	}
	
	@Override
	public void computeScroll() {
		final int childCount = getChildCount();
		float scrimOpacity = 0;
		for (int i = 0; i < childCount; i++) {
			final float onscreen = ((LayoutParams) getChildAt(i).getLayoutParams()).onScreen;
			scrimOpacity = Math.max(scrimOpacity, onscreen);
		}
		mContentScrimOpacity = scrimOpacity;
		mDrawerScrimOpacity = 1.0f - scrimOpacity;

		if (mDragger.continueSettling(true)) {
			ViewCompat.postInvalidateOnAnimation(this);
		}
	}

	private static boolean hasOpaqueBackground(View v) {
		final Drawable bg = v.getBackground();
		if (bg != null) {
			return bg.getOpacity() == PixelFormat.OPAQUE;
		}
		return false;
	}
	
	@Override
	protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
		final int width = getWidth();
		final boolean drawingContent = isContentView(child);
		int clipTop = 0, clipBottom = getHeight();

		final int restoreCount = canvas.save();
		if (drawingContent) {
			final int childCount = getChildCount();
			for (int i = 0; i < childCount; i++) {
				final View v = getChildAt(i);
				if (v == child || v.getVisibility() != VISIBLE || !hasOpaqueBackground(v) || isContentView(v) || v.getWidth() < width) {
					continue;
				}

				final int vtop = v.getTop();
				if (vtop < clipBottom)
					clipBottom = vtop;
			}
			canvas.clipRect(0, clipTop, getWidth(), clipBottom);
		}
		final boolean result = super.drawChild(canvas, child, drawingTime);
		canvas.restoreToCount(restoreCount);

		if (mContentScrimOpacity > 0 && drawingContent) {
			final int baseAlpha = (mContentScrimColor & 0xff000000) >>> 24;
			final int imag = (int) (baseAlpha * mContentScrimOpacity);
			final int color = imag << 24 | (mContentScrimColor & 0xffffff);
			mContentScrimPaint.setColor(color);
			
			canvas.drawRect(0, clipTop, getWidth(), clipBottom, mContentScrimPaint);
		} else if (mShadow != null) {
			final int shadowHeight = mShadow.getIntrinsicHeight();
			final int childTop = child.getTop();
			final int showing = getHeight() - childTop;
			final int drawerPeekDistance = mDragger.getEdgeSize();
			final float alpha = Math.max(0, Math.min((float) showing / drawerPeekDistance, 1.f));
			mShadow.setBounds(child.getLeft(), childTop - shadowHeight, child.getRight(), childTop);
			mShadow.setAlpha((int) (0xff * alpha));
			mShadow.draw(canvas);
		}

		final boolean drawingDrawer = !isContentView(child);
		if (mDrawerScrimOpacity > 0 && drawingDrawer) {
			final int baseAlpha = (mDrawerScrimColor & 0xff000000) >>> 24;
			final int imag = (int) (baseAlpha * mDrawerScrimOpacity);
			final int color = imag << 24 | (mDrawerScrimColor & 0xffffff);
			mDrawerScrimPaint.setColor(color);

			// Draw only over drawer content not over actual drawer bottom bar
			canvas.drawRect(0, child.getTop() + mVisiblePartHeight, getWidth(), child.getBottom(), mDrawerScrimPaint);
		}

		return result;
	}
	
	private boolean isContentView(View child) {
		return child.getId() == android.R.id.content;
	}
	
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		boolean interceptForDrag = mDragger.shouldInterceptTouchEvent(ev);
		boolean interceptForTap = false;
		
		final int action = MotionEventCompat.getActionMasked(ev);
		switch (action) {
		case MotionEvent.ACTION_DOWN: {
			final float x = ev.getX();
			final float y = ev.getY();
			mInitialMotionX = x;
			mInitialMotionY = y;
			final View touchedView = mDragger.findTopChildUnder((int) x, (int) y);
			if (isContentView(touchedView)){
				if(mContentScrimOpacity > 0){
					interceptForTap = true;
				}
			} else if (isBottomBarTouched(touchedView, ev)){
				mBottomBarTouched = true; 
			}
			
			break;
		}
		}
		
		if(interceptForDrag && !mBottomBarTouched){
			interceptForDrag = false;
		}

		return interceptForDrag || interceptForTap;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		mDragger.processTouchEvent(ev);

		final int action = ev.getAction();
		switch (action & MotionEventCompat.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN: {
			final float x = ev.getX();
			final float y = ev.getY();
			mInitialMotionX = x;
			mInitialMotionY = y;
			mAlwaysInTapRegion = true;
			break;
		}
		case MotionEvent.ACTION_MOVE: {
			final float x = ev.getX();
			final float y = ev.getY();
			final int dx = (int) (x - mInitialMotionX);
			final int dy = (int) (y - mInitialMotionY);
			final int distance = (dx * dx) + (dy * dy);
			if (distance > mTouchSlopSquare) {
				mAlwaysInTapRegion = false;
			}
			
			return false;
		}
		case MotionEvent.ACTION_UP: {
			final float x = ev.getX();
			final float y = ev.getY();
			final View touchedView = mDragger.findTopChildUnder((int) x, (int) y);

			if (touchedView == null) {
				return false;
			}
			
			if(isContentView(touchedView)){
				if(mContentScrimOpacity > 0){
					closeDrawer();
				}
			} else {
				if(!mBottomBarTouched){
					// Ignore tap that is outside of bottom bar region (visible part)
					return false;
				}

				mBottomBarTouched = false;
				
				if(!mAlwaysInTapRegion){
					// Do not continue if this is a side effect of drag
					return false;
				}
				
				final LayoutParams lp = (LayoutParams) touchedView.getLayoutParams();
				if (lp.knownOpen) {
					closeDrawerView(touchedView);
				} else {
					openDrawerView(touchedView);
				}
			}
			
			break;
		}
		case MotionEvent.ACTION_CANCEL: {
			mBottomBarTouched = false;
			break;
		}
		}

		return true;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && hasVisibleDrawer()) {
			KeyEventCompat.startTracking(event);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			final View visibleDrawer = findVisibleDrawer();
			if (visibleDrawer != null) {
				closeDrawerView(visibleDrawer);
			}
			return visibleDrawer != null;
		}
		return super.onKeyUp(keyCode, event);
	}
	
	public void closeDrawer() {
		final View drawerView = findDrawer();
		if(drawerView != null){
			closeDrawerView(drawerView);
		}
	}

	private void closeDrawerView(View drawerView) {
		if (isContentView(drawerView)) {
			throw new IllegalArgumentException("View " + drawerView + " is not a sliding drawer");
		}

		if (mFirstLayout) {
			final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
			lp.onScreen = 0.f;
			lp.knownOpen = false;
		} else {
			final int top = getHeight() - mVisiblePartHeight;
			mDragger.smoothSlideViewTo(drawerView, drawerView.getLeft(), top);
		}

		invalidate();
	}
	
	private void openDrawerView(View drawerView) {
		if (isContentView(drawerView)) {
			throw new IllegalArgumentException("View " + drawerView + " is not a sliding drawer");
		}

		if (mFirstLayout) {
			final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
			lp.onScreen = 1.f;
			lp.knownOpen = true;
		} else {
			final int top = getHeight() - drawerView.getHeight();
			mDragger.smoothSlideViewTo(drawerView, drawerView.getLeft(), top);
		}

		invalidate();
	}
	
	private boolean hasVisibleDrawer() {
		return findVisibleDrawer() != null;
	}

	private View findVisibleDrawer() {
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			if (!isContentView(child) && isDrawerVisible(child)) {
				return child;
			}
		}
		return null;
	}

	private boolean isBottomBarTouched(View touchedView, MotionEvent ev) {
		if(isContentView(touchedView)){
			throw new IllegalArgumentException("View " + touchedView + " is not a Drawer view.");
		}
		
		final float touchedY = ev.getY();
		final float touchedViewY = touchedY - touchedView.getTop(); 

		if(touchedViewY < mVisiblePartHeight){
			return true;
		}
		
		return false;
	}

	@Override
	protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
	}

	@Override
	protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof LayoutParams ? new LayoutParams((LayoutParams) p) : p instanceof ViewGroup.MarginLayoutParams ? new LayoutParams((MarginLayoutParams) p)
				: new LayoutParams(p);
	}

	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof LayoutParams && super.checkLayoutParams(p);
	}

	@Override
	public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new LayoutParams(getContext(), attrs);
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		final SavedState savedState = (SavedState) state;
		super.onRestoreInstanceState(savedState.getSuperState());

		if (savedState.drawerOpen) {
			final View toOpen = findDrawer();
			if (toOpen != null) {
				openDrawerView(toOpen);
			}
		}
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();

		final SavedState savedState = new SavedState(superState);

		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			if (isContentView(child)) {
				continue;
			}

			final LayoutParams lp = (LayoutParams) child.getLayoutParams();
			if (lp.knownOpen) {
				savedState.drawerOpen = true;
				break;
			}
		}

		return savedState;
	}

	public static class LayoutParams extends ViewGroup.MarginLayoutParams {
		float onScreen;
		boolean knownOpen;

		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);
		}

		public LayoutParams(int width, int height) {
			super(width, height);
		}

		public LayoutParams(LayoutParams source) {
			super(source);
		}

		public LayoutParams(ViewGroup.LayoutParams source) {
			super(source);
		}

		public LayoutParams(ViewGroup.MarginLayoutParams source) {
			super(source);
		}
	}

	private class ViewDragCallback extends ViewDragHelper.Callback {
		private ViewDragHelper mDragger;

		public void setDragger(ViewDragHelper dragger) {
			mDragger = dragger;
		}

		@Override
		public boolean tryCaptureView(View child, int pointerId) {
			return !isContentView(child);
		}

		@Override
		public void onViewDragStateChanged(int state) {
			updateDrawerState(state, mDragger.getCapturedView());
		}

		@Override
		public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
			float offset;
			final int childHeight = changedView.getHeight();
			final int openedDrawerTop = getHeight() - childHeight;
			
			// This reverses the positioning shown in onLayout.
			offset = 1 - ((float)(top - openedDrawerTop) / (childHeight - mVisiblePartHeight));

			setDrawerViewOffset(changedView, offset);

			final View contentView = findContent();
			final boolean isContentViewVisible = offset == 1.f
					&& changedView.getMeasuredHeight() == getMeasuredHeight();
			
			contentView.setVisibility(isContentViewVisible ? INVISIBLE : VISIBLE);

			invalidate();
		}

		@Override
		public void onViewReleased(View releasedChild, float xvel, float yvel) {
			// Offset is how open the drawer is, therefore left/right values
			// are reversed from one another.
			final float offset = getDrawerViewOffset(releasedChild);
			final int childHeight = releasedChild.getHeight();

			int top;
			final int height = getHeight();
			
			final float dragSlopPerc = 0.1f;
			final float minimumDrag = isDrawerOpen(releasedChild) ? 1.f - dragSlopPerc : dragSlopPerc; 
			
			top = yvel < 0 || yvel == 0 && offset > minimumDrag ? height - childHeight : height - mVisiblePartHeight;

			if (offset < 0) {
				top = height - mVisiblePartHeight;
				setDrawerViewOffset(releasedChild, 0);
			}

			mDragger.settleCapturedViewAt(releasedChild.getLeft(), top);
			invalidate();
		}

		@Override
		public int getViewVerticalDragRange(View child) {
			return child.getHeight() - mVisiblePartHeight;
		}

		@Override
		public int clampViewPositionHorizontal(View child, int left, int dx) {
			return child.getLeft();
		}

		@Override
		public int clampViewPositionVertical(View child, int top, int dy) {
			final int childHeight = child.getHeight();
			final int bottomLimit = getHeight() - mVisiblePartHeight;
			final int topLimit = getHeight() - childHeight;
			
			if(top > bottomLimit){
				top = bottomLimit;
			} else if(top < topLimit){
				top = topLimit;
			}
			
			return top;
		}
	}

	/**
	 * State persisted across instances
	 */
	protected static class SavedState extends BaseSavedState {
		boolean drawerOpen = false;

		public SavedState(Parcel in) {
			super(in);
			drawerOpen = in.readByte() == 1;
		}

		public SavedState(Parcelable superState) {
			super(superState);
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeByte((byte) (drawerOpen ? 1 : 0));
		}

		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			@Override
			public SavedState createFromParcel(Parcel source) {
				return new SavedState(source);
			}

			@Override
			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}

	class AccessibilityDelegate extends AccessibilityDelegateCompat {
		private final Rect mTmpRect = new Rect();

		@Override
		public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
			final AccessibilityNodeInfoCompat superNode = AccessibilityNodeInfoCompat.obtain(info);
			super.onInitializeAccessibilityNodeInfo(host, superNode);

			info.setSource(host);
			final ViewParent parent = ViewCompat.getParentForAccessibility(host);
			if (parent instanceof View) {
				info.setParent((View) parent);
			}
			copyNodeInfoNoChildren(info, superNode);

			superNode.recycle();

			final int childCount = getChildCount();
			for (int i = 0; i < childCount; i++) {
				final View child = getChildAt(i);
				if (!filter(child)) {
					info.addChild(child);
				}
			}
		}

		@Override
		public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child, AccessibilityEvent event) {
			if (!filter(child)) {
				return super.onRequestSendAccessibilityEvent(host, child, event);
			}
			return false;
		}

		public boolean filter(View child) {
			final View openDrawer = findOpenDrawer();
			return openDrawer != null && openDrawer != child;
		}

		/**
		 * This should really be in AccessibilityNodeInfoCompat, but there
		 * unfortunately seem to be a few elements that are not easily cloneable
		 * using the underlying API. Leave it private here as it's not
		 * general-purpose useful.
		 */
		private void copyNodeInfoNoChildren(AccessibilityNodeInfoCompat dest, AccessibilityNodeInfoCompat src) {
			final Rect rect = mTmpRect;

			src.getBoundsInParent(rect);
			dest.setBoundsInParent(rect);

			src.getBoundsInScreen(rect);
			dest.setBoundsInScreen(rect);

			dest.setVisibleToUser(src.isVisibleToUser());
			dest.setPackageName(src.getPackageName());
			dest.setClassName(src.getClassName());
			dest.setContentDescription(src.getContentDescription());

			dest.setEnabled(src.isEnabled());
			dest.setClickable(src.isClickable());
			dest.setFocusable(src.isFocusable());
			dest.setFocused(src.isFocused());
			dest.setAccessibilityFocused(src.isAccessibilityFocused());
			dest.setSelected(src.isSelected());
			dest.setLongClickable(src.isLongClickable());

			dest.addAction(src.getActions());
		}
	}
}
