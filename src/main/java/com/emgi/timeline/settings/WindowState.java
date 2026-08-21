package com.emgi.timeline.settings;

public record WindowState(double x, double y, double width, double height, boolean maximized)
{
    public static final double MIN_USABLE = 200;

    public static final double MIN_VISIBLE = 80;

    public boolean isUsable()
    {
        if(!Double.isFinite(x) || !Double.isFinite(y)
            || !Double.isFinite(width) || !Double.isFinite(height))
        {
            return false;
        }

        return width >= MIN_USABLE && height >= MIN_USABLE;
    }

    public boolean intersects(double screenX, double screenY,
                              double screenWidth, double screenHeight)
    {
        if(!isUsable())
        {
            return false;
        }

        double overlapX = Math.min(x + width, screenX + screenWidth) - Math.max(x, screenX);
        double overlapY = Math.min(y + height, screenY + screenHeight) - Math.max(y, screenY);

        return overlapX >= MIN_VISIBLE && overlapY >= MIN_VISIBLE;
    }
}
