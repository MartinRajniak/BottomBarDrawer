package sk.rajniak.bottombardrawerexample;

import sk.rajniak.bottombardrawer.BottomBarDrawerLayout;
import sk.rajniak.bottombardrawer.BottomBarDrawerLayout.DrawerListener;
import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		final ActionBar actionBar = getActionBar();

		final BottomBarDrawerLayout slideInLayout = (BottomBarDrawerLayout) findViewById(R.id.drawerLayout);
		slideInLayout.setDrawerShadow(R.drawable.drawer_shadow);
		slideInLayout.setDrawerListener(new DrawerListener() {

			@Override
			public void onDrawerStateChanged(int newState) {
 
			}

			@Override
			public void onDrawerSlide(View drawerView, float slideOffset) {
				if (actionBar != null) {
					if (slideOffset > 0.8f) {
						actionBar.hide();
					} else {
						actionBar.show();
					}
				}
			}

			@Override
			public void onDrawerOpened(View drawerView) {

			}

			@Override
			public void onDrawerClosed(View drawerView) {

			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
