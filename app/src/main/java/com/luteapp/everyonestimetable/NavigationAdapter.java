package com.luteapp.everyonestimetable;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * This is to manage my left drawer navigation menu.
 */
public class NavigationAdapter extends ArrayAdapter<NavigationItemAdapter>
{
    static final String TAG = "EveryonesTimetable";
    // The menu items
    // Note that the index of the "Update timetables" item is hardcoded in updateAllTimetables()
    // And the other indices in MainActivity.DrawerItemClickListener
    static String[] menuItems = new String[] {/*"Home", */"Update timetables", /*"My profile",*/"Premium", "Log out", "About"};
    
    /**
     * Create the navigation adapter.
     */
    static NavigationAdapter getNavigationAdapter(Context context)
    {    
        NavigationAdapter navigationAdapter = new NavigationAdapter(context);
        
        for (int i = 0; i < menuItems.length; i++) 
        {
            String title = menuItems[i];                            
            NavigationItemAdapter navigationItem = new NavigationItemAdapter(title, 0);   
            navigationAdapter.addItem(navigationItem);
        }
        
        return navigationAdapter;                       
    }
    
    public NavigationAdapter(Context context)
    {
        super(context, 0);
    }

    public void addHeader(String title)
    {
        add(new NavigationItemAdapter(title, 0, true, false));
    }

    public void addItem(String title, int icon)
    {
        add(new NavigationItemAdapter(title, icon, false, false));
    }

    public void addItem(NavigationItemAdapter itemModel)
    {
        add(itemModel);
    }
    
    @Override
    public int getViewTypeCount()
    {
        // Types of views a row can be: header or normal
        return 2;
    }
    
    @Override
    public int getItemViewType(int position)
    {
        return getItem(position).isHeader ? 0 : 1;
    }
    
    @Override
    public boolean isEnabled(int position)
    {
        return !getItem(position).isHeader && !getItem(position).isWorking;
    }
    
    /**
     * The widgets that make up a menu item. Not useful outside this class because objects
     * of ViewHolder type get recreated based on their backing NavigationItemAdapter.   
     */
    private static class ViewHolder
    {
        public final ImageView icon;		
        public final TextView title;
        public final ProgressBar progressSpinner;
        public final View viewNavigation;
        
        public ViewHolder(TextView title, ProgressBar progressSpinner, ImageView icon,View viewNavigation)
        {
            this.title = title;
            this.progressSpinner = progressSpinner;
            this.icon = icon;
            this.viewNavigation = viewNavigation;
        }
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) 
    {
        ViewHolder holder = null;		
        View view = convertView;
        NavigationItemAdapter item = getItem(position);
        
        // i.e. If don't already have it built - inflate it now.
        if (view == null)
        {
            int layout = 0;
            if (item.isHeader)
                layout = R.layout.navigation_header_title;
            else
                layout = R.layout.navigation_item_spinner;
            view = LayoutInflater.from(getContext()).inflate(layout, null);
            
            // Store all the widgets in a ViewHolder, and store a reference to that in the view's tag.
            // I guess the point is that this makes the children easier to reference later in this function.
            TextView txttitle = (TextView) view.findViewById(R.id.title);
            ProgressBar progressSpinner = (ProgressBar)view.findViewById(R.id.progressSpinner);
            ImageView imgIcon = (ImageView) view.findViewById(R.id.icon);
            View viewNavigation = (View) view.findViewById(R.id.viewNavigation);
            view.setTag(new ViewHolder(txttitle, progressSpinner, imgIcon, viewNavigation));
        }

        holder = (ViewHolder)view.getTag();
        
        // Show the title
        holder.title.setText(item.title);
        
        if (!item.isHeader)
        {
            // Show the icon if have one configured for this item
            if (item.icon != 0)
            {
                holder.icon.setVisibility(View.VISIBLE);
                holder.icon.setImageResource(item.icon);
            }
            else
                holder.icon.setVisibility(View.GONE);
            
            // Show the navigation bar (little vertical blue stripe on the left)
    //        if (checkedItems.contains(Integer.valueOf(position))) {
    //            holder.viewNavigation.setVisibility(View.VISIBLE);
    //        } else {				
                holder.viewNavigation.setVisibility(View.GONE);				
    //        }
        }
        
        view.setBackgroundResource(R.drawable.selector_item_navigation);
        
        if (item.isWorking)
        {
            holder.progressSpinner.setVisibility(View.VISIBLE);
            holder.title.setEnabled(false);
        }
        else
        {
            holder.progressSpinner.setVisibility(View.GONE);
            holder.title.setEnabled(true);
        }
        
        return view;		
    }

}
