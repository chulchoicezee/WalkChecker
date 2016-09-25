package com.chulgee.walkchecker.adapter;

import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
 * Created by chulchoice on 2016-05-21.
 */
public abstract class CurAdapter extends BaseAdapter{

    public abstract View getRowView();

    public abstract void setRow( Cursor cursor, int idx, View view, ViewGroup viewGroup );

    private Cursor c;

    public CurAdapter(Cursor $c){
        c = $c;
    }

    @Override
    public int getCount() {
        return c!=null?c.getCount():0;
    }

    @Override
    public Object getItem(int $position) { return $position; }

    @Override
    public long getItemId(int $position) {
        return $position;
    }

    @Override
    public View getView(int position, View v, ViewGroup parent) {

        if(v == null) v = getRowView();

        c.moveToPosition(position);

        setRow(c, position, v, parent);

        return v;
    }

    public void update(Cursor $c){
        c = $c;
        notifyDataSetChanged();
    }

    public Cursor getCursor(){
        return c;
    }
}
