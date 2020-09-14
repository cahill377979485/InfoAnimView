package com.cahill377979485.infoanimview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

/**
 * @author 文琳
 * @time 2020/9/8 11:57
 * @desc 默认显示圆形头像，加一个信息按钮，点击之后弹出会动的小窗口，显示具体信息。过两秒之后自动缩回去。
 */
public class InfoAnimView extends View {
    private int viewWidth, viewHeight;//宽、高
    private Paint paint;
    private Path path;//路径
    private Rect rect;//用来计算文字宽度的矩形
    @ColorInt
    private int mainColor;//主色调，包括圆形头像内边框、按钮背景、大小圆连接部分以及信息的背景
    private float civBorderWidth;//圆形头像的边框宽度
    private float civWidth, civHeight;//圆形头像的宽高
    private float iconRadius;//按钮的半径
    private float infoWidth;//信息布局的宽度
    private Drawable drawableIcon;//按钮图标
    private Drawable drawableCiv;//头像
    private String desc;//信息文本
    private float descTextSize;//信息文字大小
    @ColorInt
    private int descTextColor;//信息文字颜色
    private static final float ROOT_2 = 1.414213f;//根号2的近似值
    private static final float SIN_22_Dot_5 = 0.38268f;//sin22.5°的近似值
    private static final float COS_22_Dot_5 = 0.92388f;//cos22.5°的近似值
    private static final float SIN_10 = 0.173648f;//sin10°的近似值
    private static final float COS_10 = 0.9848f;//cos10°的近似值
    private static final int ANIM_DURATION = 350;
    private static final int AUTO_HIDE_INFO_DURATION = 2000;
    private static final int STATE_NORMAL = 0;//普通状态
    private static final int STATE_ANIM = 1;//动画中
    private int currentState;//当前状态
    private ValueAnimator animatorCircle;//圆形头像的动画
    private ValueAnimator animatorInfo;//信息布局的动画
    private int deltaCircle;//小圆的偏移值，跟动画相关
    private int deltaInfo;//信息布局的偏移值，跟动画相关
    private boolean showInfo;//用来控制是否显示信息布局
    private int alphaCircle = 255, alphaInfo = 255;//按钮和信息布局的不透明度
    private boolean isRelease;//用来控制是否已经决定释放资源了，如果是就拦截操作
    private clickListener listener;//监听
    private Bitmap bitmap;//头像的Bitmap图片
    private PorterDuffXfermode porterDuffXfermode;//图像合成的模式

    public InfoAnimView(Context context) {
        this(context, null);
    }

    public InfoAnimView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.InfoAnimView);
        mainColor = typedArray.getColor(R.styleable.InfoAnimView_iav_civ_border_color, Color.WHITE);
        civBorderWidth = typedArray.getDimensionPixelSize(R.styleable.InfoAnimView_iav_civ_border_width, DensityUtil.dip2px(context, 3));
        drawableIcon = typedArray.getDrawable(R.styleable.InfoAnimView_iav_icon);
        drawableCiv = typedArray.getDrawable(R.styleable.InfoAnimView_iav_civ);
        civWidth = typedArray.getDimensionPixelSize(R.styleable.InfoAnimView_iav_civ_width, DensityUtil.dip2px(context, 120));
        civHeight = typedArray.getDimensionPixelSize(R.styleable.InfoAnimView_iav_civ_height, DensityUtil.dip2px(context, 120));
        desc = typedArray.getString(R.styleable.InfoAnimView_iav_desc);
        descTextSize = typedArray.getDimension(R.styleable.InfoAnimView_iav_desc_text_size, DensityUtil.dip2px(context, 25));
        descTextColor = typedArray.getColor(R.styleable.InfoAnimView_iav_desc_text_color, Color.BLACK);
        typedArray.recycle();
        init();
    }

    /**
     * 初始化
     */
    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);//抗锯齿
        paint.setDither(true);//防抖动
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        path = new Path();
        rect = new Rect();
        paint.setTextSize(descTextSize);
        porterDuffXfermode = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
    }

    /**
     * 设置点击事件
     *
     * @param clickListener 点击事件
     */
    public void setClickListener(clickListener clickListener) {
        listener = clickListener;
    }

    /**
     * 设置信息文本
     *
     * @param desc 信息文本
     */
    public void setDesc(String desc) {
        if (isRelease) return;
        this.desc = desc;
        postInvalidate();
    }

    /**
     * 设置头像地址
     *
     * @param path 头像保存的地址
     */
    public void setCivPath(String path) {
        bitmap = BitmapFactory.decodeFile(path);
        bitmap = handleBitmap();
        postInvalidate();
    }

    /**
     * 显示信息布局
     */
    public void showInfo() {
        if (isRelease) return;
        startCircleAnim(false);
        startInfoAnim(true);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                showBtn();
            }
        }, AUTO_HIDE_INFO_DURATION);
    }

    /**
     * 显示按钮
     */
    private void showBtn() {
        if (isRelease) return;
        startInfoAnim(false);
        startCircleAnim(true);
    }

    /**
     * 开始按钮的动画
     *
     * @param show 是否是显示的动画
     */
    private void startCircleAnim(boolean show) {
        if (isRelease) return;
        if (animatorCircle != null) animatorCircle.cancel();
        final int maxDistance = (int) (iconRadius * 2 * (ROOT_2 / 2f));
        if (show) animatorCircle = ValueAnimator.ofInt(maxDistance, 0);
        else animatorCircle = ValueAnimator.ofInt(0, maxDistance);
        animatorCircle.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                deltaCircle = (int) animation.getAnimatedValue();
                alphaCircle = 255 - (deltaCircle / maxDistance * 255);
                if (alphaCircle < 0) alphaCircle = 0;
                if (alphaCircle > 255) alphaCircle = 255;
                postInvalidate();
            }
        });
        if (show) animatorCircle.setInterpolator(new BounceInterpolator());//添加回弹插值器
        else animatorCircle.setInterpolator(new AccelerateInterpolator());//添加加速插值器
        animatorCircle.setDuration(show ? ANIM_DURATION + 150 : ANIM_DURATION - 150);//显示的时候慢点，隐藏的时候快点
        animatorCircle.start();
    }

    /**
     * 开始信息布局的动画
     *
     * @param show 是否是显示的动画
     */
    private void startInfoAnim(boolean show) {
        if (isRelease) return;
        if (animatorInfo != null) animatorInfo.cancel();
        final int maxDistance = (int) (iconRadius * 3);
        if (show) animatorInfo = ValueAnimator.ofInt(maxDistance, 0);
        else animatorInfo = ValueAnimator.ofInt(0, maxDistance);
        animatorInfo.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                deltaInfo = (int) animation.getAnimatedValue();
                alphaInfo = 255 - (deltaInfo / maxDistance * 255);
                if (alphaInfo < 0) alphaInfo = 0;
                if (alphaInfo > 255) alphaInfo = 255;
                //根据不透明度来设置是否绘制信息布局
                showInfo = alphaInfo > 64;//取256的四分之一，换算成0到1来表示的话，就是0.25。
                currentState = alphaInfo > 0 ? STATE_ANIM : STATE_NORMAL;//上面的小圆的就不加这句了，因为info的动画时间更长，这里设置好就行了
                postInvalidate();
            }
        });
        animatorInfo.setInterpolator(new OvershootInterpolator());//添加超越插值器
        animatorInfo.setDuration(ANIM_DURATION);
        animatorInfo.start();
    }

    /**
     * 释放资源
     */
    public void release() {
        isRelease = true;
        postInvalidate();
        drawableIcon = null;
        drawableCiv = null;
        if (animatorCircle != null) animatorCircle.cancel();
        animatorCircle = null;
        if (animatorInfo != null) animatorInfo.cancel();
        animatorInfo = null;
        if (bitmap != null) bitmap.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isRelease) return;
        iconRadius = (ROOT_2 - 1) * civHeight / 2f;
        int iconPadding = (int) (iconRadius / 2f); //按钮图片的内边距
        //画info，放在第一步，避免显示时遮住其他部分
        if (showInfo) {
            paint.setAlpha(alphaInfo);
            paint.setColor(mainColor);
            paint.getTextBounds(desc, 0, desc.length(), rect);
            int textWidth = rect.width();
            infoWidth = iconRadius * 2 + textWidth + iconRadius;//这里的iconRadius作为左右两边的padding的和，不合适的话可以调
            canvas.drawRoundRect(viewWidth / 2f - infoWidth / 2f, viewHeight - civHeight - iconRadius * 3 + deltaInfo,
                    viewWidth / 2f + infoWidth / 2f, viewHeight - civHeight - iconRadius + deltaInfo,
                    iconRadius, iconRadius, paint);

            //画小三角形
            path.reset();
            path.addCircle(viewWidth / 2f, viewHeight - civHeight - iconRadius / 2f + deltaInfo, iconRadius / 8, Path.Direction.CW);
            canvas.drawPath(path, paint);
            path.reset();
            float triX = viewWidth / 2f - iconRadius / 8 * COS_22_Dot_5;
            float triY = viewHeight - civHeight - iconRadius / 2f + iconRadius / 8f * SIN_22_Dot_5 + deltaInfo;
            path.moveTo(triX, triY);//小三角形底点
            path.quadTo(viewWidth / 2f - iconRadius / 2f, viewHeight - civHeight - iconRadius + deltaInfo,
                    viewWidth / 2f - iconRadius, viewHeight - civHeight - iconRadius - 1 + deltaInfo);//左弧贝塞尔曲线，多减掉的2是为了防止出现缝隙
            path.lineTo(viewWidth / 2f + iconRadius, viewHeight - civHeight - iconRadius - 1 + deltaInfo);//连到右弧端点
            path.quadTo(viewWidth / 2f + iconRadius / 2f, viewHeight - civHeight - iconRadius + deltaInfo,
                    viewWidth / 2f + iconRadius / 8 * COS_22_Dot_5, triY);//右弧贝塞尔曲线连到三角形底点
            path.close();
            canvas.drawPath(path, paint);

            //画info的图片
            drawableIcon.setBounds((int) (viewWidth / 2f - infoWidth / 2f) + iconPadding, (int) (viewHeight - civHeight - iconRadius * 3 + deltaInfo) + iconPadding,
                    (int) (viewWidth / 2f - infoWidth / 2f + iconRadius * 2) - iconPadding, (int) (viewHeight - civHeight - iconRadius + deltaInfo) - iconPadding);
            drawableIcon.draw(canvas);

            //画info的文字
            paint.setColor(descTextColor);
            canvas.drawText(desc, viewWidth / 2f - infoWidth / 2f + iconRadius * 2, viewHeight - civHeight - iconRadius + deltaInfo - iconPadding, paint);
        }

        //画连接两圆的异形梯形部分
        paint.setAlpha(alphaCircle);
        paint.setColor(mainColor);
        path.reset();
        path.moveTo(viewWidth / 2f + civWidth / 2f * SIN_10, viewHeight - civHeight / 2f * (1 + COS_10));
        float delta = iconRadius / 2;//控制点与两圆切点的水平和竖直方向的偏移值，用来控制二阶贝塞尔曲线的线的弯曲程度
        path.quadTo(viewWidth / 2f + civWidth / 2f * ROOT_2 / 2 - delta, viewHeight - civHeight / 2f - civHeight / 2f * ROOT_2 / 2 - delta,
                viewWidth / 2f + civWidth / 2f - iconRadius * COS_22_Dot_5 - deltaCircle, viewHeight - civHeight - iconRadius * SIN_22_Dot_5 + deltaCircle);
        path.lineTo(viewWidth / 2f + civWidth / 2f + iconRadius * SIN_22_Dot_5 - deltaCircle, viewHeight - civHeight + iconRadius * COS_22_Dot_5 + deltaCircle);
        path.quadTo(viewWidth / 2f + civWidth / 2f * ROOT_2 / 2 - deltaCircle + delta, viewHeight - civHeight / 2f - civHeight / 2f * ROOT_2 / 2 + deltaCircle + delta,
                viewWidth / 2f + civWidth / 2f * COS_10 - deltaCircle, viewHeight - civHeight / 2f * (1 + SIN_10) + deltaCircle);
        path.close();
        canvas.drawPath(path, paint);

        //画小圆
        path.reset();
        path.addCircle(viewWidth / 2f + civWidth / 2f - deltaCircle, viewHeight - civHeight + deltaCircle, iconRadius, Path.Direction.CW);
        canvas.drawPath(path, paint);

        //画小圆的图片
        drawableIcon.setBounds((int) (viewWidth / 2f + civWidth / 2f - iconRadius - deltaCircle) + iconPadding, (int) (viewHeight - civHeight - iconRadius + deltaCircle) + iconPadding,
                (int) (viewWidth / 2f + civWidth / 2f + iconRadius - deltaCircle) - iconPadding, (int) (viewHeight - civHeight + iconRadius + deltaCircle) - iconPadding);
        drawableIcon.draw(canvas);

        //画圆形图片的背景
        paint.setAlpha(255);
        canvas.drawCircle(viewWidth / 2f, viewHeight - civHeight / 2f, civHeight / 2f, paint);

        //画头像
        if (bitmap == null) {//如果还没加载图片，则加载布局中设置的默认图片
            BitmapDrawable bd = (BitmapDrawable) drawableCiv;
            bitmap = bd.getBitmap();
            bitmap = handleBitmap();
        }
        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 255);
        canvas.drawOval((int) (viewWidth / 2f - civWidth / 2f + civBorderWidth), (int) (viewHeight - civHeight + civBorderWidth),
                (int) (viewWidth / 2f + civWidth / 2f - civBorderWidth), (int) (viewHeight - civBorderWidth), paint);
        paint.setXfermode(porterDuffXfermode);
        canvas.drawBitmap(bitmap, (int) (viewWidth / 2f - civWidth / 2f + civBorderWidth), (int) (viewHeight - civHeight + civBorderWidth), paint);
        paint.setXfermode(null);
    }

    /**
     * 生成新的Bitmap图片
     *
     * @return 所求
     */
    private Bitmap handleBitmap() {
        Matrix matrix = new Matrix();//创建一个处理图片的类
        int newW = (int) (civWidth - civBorderWidth);
        int newH = (int) (civHeight - civBorderWidth);
        int width = bitmap.getWidth();//获取图片本身的大小(宽)
        int height = bitmap.getHeight();//获取图片本身的大小(高)
        float wS = (float) newW / width;//缩放比---->这块注意这个是新的宽度/高度除以旧的宽度
        float hS = (float) newH / height;//缩放比---->这块注意这个是新的宽度/高度除以旧的宽度
        matrix.postScale(wS, hS);//这块就是处理缩放的比例
        //matrix.setScale(sX,sY);//缩放图片的质量sX表示宽0.5就代表缩放一半,sX同样
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (x > 0 && x < viewWidth && y > 0 && y < viewHeight) {//如果正在动画则拦截动作
                    postInvalidate();
                    return true;
                } else {
                    return false;
                }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (x > viewWidth / 2f + civWidth / 2f - iconRadius && y > viewHeight - civHeight - iconRadius
                        && x < viewWidth / 2f + civWidth / 2f + iconRadius && y < viewHeight - civHeight + iconRadius) {//在小圆内，这里用的是外切矩形的边界来判断，实际上并不精确，但是也没必要过于精确
                    if (currentState == STATE_ANIM) return false;
                    showInfo();
                    if (listener != null) listener.clickBtn();
                } else if (x > viewWidth / 2f - civWidth / 2f && y > viewHeight - civHeight
                        && x < viewWidth / 2f + civWidth / 2f && y < viewHeight) {//在大圆内，这里用的是外切矩形的边界来判断，实际上并不精确，但是也没必要过于精确
                    if (listener != null) listener.clickCiv();
                } else if (x > viewWidth / 2f - infoWidth / 2f && y > viewHeight - civHeight - iconRadius * 3
                        && x < viewWidth / 2f + infoWidth / 2f && y < viewHeight - civHeight - iconRadius) {//点击了信息，这里用的是信息框外切矩形的边界来判断，实际上并不精确，但是也没必要过于精确
                    if (listener != null && showInfo) listener.clickInfo(desc);
                }
                postInvalidate();
                break;
            default:
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        viewWidth = w;
        viewHeight = h;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        viewWidth = measureWidth(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        viewHeight = measureHeight(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();
        if (civWidth > viewWidth / 2f || civHeight > viewWidth / 2f) civHeight = civWidth = viewWidth / 2f;//如果布局中设置的头像大小超过了整个控件的一半宽，则设置头像宽为控件的一半
        setMeasuredDimension(viewWidth, viewHeight);
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);//不要调用super.onMeasure()，而是上句的setMeasuredDimension()
    }

    /**
     * 支持自适应宽度
     *
     * @param measureSpec 单位
     * @return 所求
     */
    private int measureWidth(int measureSpec) {
        int result;
        int mode = MeasureSpec.getMode(measureSpec);
        int size = MeasureSpec.getSize(measureSpec);
        if (mode == MeasureSpec.EXACTLY) {
            result = size;
        } else {
            result = DensityUtil.dip2px(getContext(), 240);//控件最大的宽，根据自己的需要更改
            if (mode == MeasureSpec.AT_MOST) {
                result = Math.min(result, size);
            }
        }
        return result;
    }

    /**
     * 支持自适应高度
     *
     * @param measureSpec 单位
     * @return 所求
     */
    private int measureHeight(int measureSpec) {
        int result;
        int mode = MeasureSpec.getMode(measureSpec);
        int size = MeasureSpec.getSize(measureSpec);
        if (mode == MeasureSpec.EXACTLY) {
            result = size;
        } else {
            result = DensityUtil.dip2px(getContext(), 260);//控件最大的高，根据自己的需要更改
            if (mode == MeasureSpec.AT_MOST) {
                result = Math.min(result, size);
            }
        }
        return result;
    }

    /**
     * 对外提供三个点击事件的接口
     */
    public interface clickListener {
        void clickCiv();

        void clickBtn();

        void clickInfo(String desc);
    }
}
