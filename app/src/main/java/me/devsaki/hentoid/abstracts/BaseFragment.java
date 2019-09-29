package me.devsaki.hentoid.abstracts;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

/**
 * Created by avluis on 04/10/2016.
 * Basic Fragment Abstract Class
 * Implementations receive an onBackPressed handled by the hosting activity.
 */
public abstract class BaseFragment extends Fragment {

    private BackInterface backInterface;

    public abstract boolean onBackPressed();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!(getActivity() instanceof BackInterface)) {
            throw new ClassCastException(
                    "Hosting activity must implement BackInterface.");
        } else {
            backInterface = (BackInterface) getActivity();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        backInterface.addBackInterface(this);
    }

    public interface BackInterface {
        void addBackInterface(BaseFragment fragment);
    }
}
