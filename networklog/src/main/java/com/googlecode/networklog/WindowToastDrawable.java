package com.googlecode.networklog;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.shapes.RoundRectShape;

public class WindowToastDrawable extends Drawable {

	Paint paint=null;

	RectF rectF;
	int width=-1;
	int height=-1;

	public WindowToastDrawable() {
		paint = new Paint();

		paint.setColor(Color.BLACK);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeJoin(Paint.Join.ROUND);

		paint.setAntiAlias(true);
		paint.setStyle(Paint.Style.FILL);
		rectF = new RectF();

	}


	@Override
	public int getIntrinsicWidth() {
		if (width==-1){
			return 100;
		}
		return super.getIntrinsicWidth();
	}

	@Override
	public int getIntrinsicHeight() {
		if (height==-1){
			return 100;
		}
		return super.getIntrinsicHeight();
	}

	@Override
	public void draw(Canvas canvas) {
		canvas.drawRect(rectF,paint);
	}

	@Override
	public void setAlpha(int i) {
		paint.setAlpha(i);
//		invalidateSelf();
	}


	@Override
	public void setColorFilter(ColorFilter colorFilter) {
		paint.setColorFilter(colorFilter);
	}

	@Override
	public int getOpacity() {
		return PixelFormat.UNKNOWN;
	}
}
