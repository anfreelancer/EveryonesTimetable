package com.luteapp.everyonestimetable;

/**
 * This represents one item in the navigation drawer. The item gets drawn
 * based on data in this structure.
 */
public class NavigationItemAdapter
{
    public String title;
    public int icon;
    public boolean isHeader;
    public boolean isWorking;

    public NavigationItemAdapter(String title, int icon, boolean isHeader, boolean isWorking)
    {
        this.title = title;
        this.icon = icon;
        this.isHeader = isHeader;
        this.isWorking = isWorking;
    }
    
    public NavigationItemAdapter(String title, int icon)
    {
        this(title, icon, false, false);
    }
}
