package me.devsaki.hentoid.ui;

import android.content.Context;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class CarouselDecorator {

    private final Context context;
    private final int itemLayout;
    private final CarouselAdapter adapter;
    private final LinearLayoutManager layoutManager;

    private int pageCount;
    private OnPageChangeListener onPageChangeListener;

    public CarouselDecorator(Context context, @LayoutRes int itemLayout) {
        this.context = context;
        this.itemLayout = itemLayout;
        adapter = new CarouselAdapter();
        layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
        adapter.notifyDataSetChanged();
    }

    public void setCurrentPage(int page) {
        layoutManager.scrollToPosition(page - 1);
    }

    public void setOnPageChangeListener(OnPageChangeListener onPageChangeListener) {
        this.onPageChangeListener = onPageChangeListener;
    }

    public void decorate(RecyclerView recyclerView) {
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new CarouselOnScrollListener());

        LinearSnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);
    }

    private class CarouselOnScrollListener extends RecyclerView.OnScrollListener {

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE) return;

            int position = layoutManager.findFirstVisibleItemPosition();
            onPageChangeListener.onPageChange(position + 1);
        }
    }

    private class CarouselAdapter extends RecyclerView.Adapter<CarouselViewHolder> {

        @NonNull
        @Override
        public CarouselViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(context);
            View view = inflater.inflate(itemLayout, parent, false);
            return new CarouselViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CarouselViewHolder holder, int position) {
            holder.textView.setText(String.valueOf(position + 1));
        }

        @Override
        public int getItemCount() {
            return pageCount;
        }
    }

    private static class CarouselViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;

        private CarouselViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }
    }

    public interface OnPageChangeListener {
        void onPageChange(int page);
    }
}