package com.codingstuff.todolist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.codingstuff.todolist.Adapter.ToDoAdapter;
import com.codingstuff.todolist.Model.ToDoModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnDialogCloseListner, DataRefreshListener {

    private RecyclerView recyclerView;
    private FloatingActionButton mFab;
    private FirebaseFirestore firestore;
    private FirebaseAuth mAuth;
    private ToDoAdapter adapter;
    private List<ToDoModel> mList;
    private Query query;
    private ListenerRegistration listenerRegistration;
    private TextView noTasksText;
    private Toolbar toolbar;
    private String userId;

    // Hằng số cho theme
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "app_theme";
    private static final int THEME_LIGHT = 0;
    private static final int THEME_DARK = 1;
    private static final int THEME_SYSTEM = 2;

    private boolean isDarkMode = false;

    // Static instance for data refresh
    private static MainActivity instance;

    public static MainActivity getInstance() {
        return instance;
    }

    private void initFirestore() {
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build();

        firestore = FirebaseFirestore.getInstance();
        firestore.setFirestoreSettings(settings);

        // Khởi tạo Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Kiểm tra người dùng đã đăng nhập chưa
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();

            // Kiểm tra xem có phải người dùng ẩn danh không
            if (currentUser.isAnonymous()) {
                // Hiển thị thông báo cho người dùng biết họ đang sử dụng chế độ ẩn danh
                Toast.makeText(this, "Bạn đang sử dụng chế độ không đăng nhập. Dữ liệu sẽ không được đồng bộ.", Toast.LENGTH_LONG).show();
            }
        } else {
            // Nếu chưa đăng nhập, chuyển về màn hình đăng nhập
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        }
    }

    @Override
    public void onDataRefreshed() {
        // Refresh data from Firestore
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
        loadData();
        Toast.makeText(this, R.string.data_refreshed, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set static instance
        instance = this;

        // Áp dụng theme trước khi setContentView
        applyTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initFirestore();

        try {
            // Thiết lập toolbar
            toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                setSupportActionBar(toolbar);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayShowTitleEnabled(false);
                }
            }

            recyclerView = findViewById(R.id.recycerlview);
            mFab = findViewById(R.id.floatingActionButton);
            noTasksText = findViewById(R.id.no_tasks_text);
        } catch (Exception e) {
            e.printStackTrace();
        }

        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));

        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AddNewTask.newInstance().show(getSupportFragmentManager() , AddNewTask.TAG);
            }
        });

        mList = new ArrayList<>();
        adapter = new ToDoAdapter(MainActivity.this , mList);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new TouchHelper(adapter));
        itemTouchHelper.attachToRecyclerView(recyclerView);
        loadData();
        recyclerView.setAdapter(adapter);
    }
    public void loadData(){
        try {
            // Sử dụng userId để truy vấn dữ liệu của người dùng hiện tại
            query = firestore.collection("users").document(userId).collection("tasks").orderBy("time" , Query.Direction.DESCENDING);

            listenerRegistration = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                    if (error != null) {
                        return;
                    }

                    if (value == null) {
                        return;
                    }

                    try {
                        for (DocumentChange documentChange : value.getDocumentChanges()){
                            if (documentChange.getType() == DocumentChange.Type.ADDED){
                                String id = documentChange.getDocument().getId();
                                ToDoModel toDoModel = documentChange.getDocument().toObject(ToDoModel.class).withId(id);
                                mList.add(toDoModel);
                                adapter.notifyDataSetChanged();
                            }
                        }

                        // Hiển thị thông báo khi không có công việc nào
                        if (noTasksText != null && recyclerView != null) {
                            if (mList.isEmpty()) {
                                recyclerView.setVisibility(View.GONE);
                                noTasksText.setVisibility(View.VISIBLE);
                            } else {
                                recyclerView.setVisibility(View.VISIBLE);
                                noTasksText.setVisibility(View.GONE);
                            }
                        }

                        if (listenerRegistration != null) {
                            listenerRegistration.remove();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDialogClose(DialogInterface dialogInterface) {
        mList.clear();
        loadData();
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // Cập nhật icon menu dựa vào trạng thái theme hiện tại
        MenuItem themeItem = menu.findItem(R.id.menu_theme);
        updateThemeIcon(themeItem);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_calendar) {
            // Chuyển đến màn hình lịch
            Intent intent = new Intent(MainActivity.this, CalendarActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_theme) {
            // Chuyển đổi theme
            toggleTheme();
            return true;
        } else if (id == R.id.menu_backup_restore) {
            // Chuyển đến màn hình sao lưu và khôi phục
            Intent intent = new Intent(MainActivity.this, BackupRestoreActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_logout) {
            // Đăng xuất
            mAuth.signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Áp dụng theme dựa trên cài đặt đã lưu
     */
    private void applyTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME, THEME_SYSTEM);

        switch (themeMode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                isDarkMode = false;
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                isDarkMode = true;
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                isDarkMode = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
    }

    /**
     * Chuyển đổi giữa chế độ sáng và tối
     */
    private void toggleTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (isDarkMode) {
            // Chuyển sang chế độ sáng
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            editor.putInt(KEY_THEME, THEME_LIGHT);
            Toast.makeText(this, R.string.light_mode, Toast.LENGTH_SHORT).show();
        } else {
            // Chuyển sang chế độ tối
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            editor.putInt(KEY_THEME, THEME_DARK);
            Toast.makeText(this, R.string.dark_mode, Toast.LENGTH_SHORT).show();
        }

        editor.apply();
    }

    /**
     * Cập nhật icon menu dựa vào trạng thái theme hiện tại
     */
    private void updateThemeIcon(MenuItem item) {
        if (item != null) {
            isDarkMode = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES);

            if (isDarkMode) {
                item.setIcon(R.drawable.ic_baseline_light_mode_24);
                item.setTitle(R.string.light_mode);
            } else {
                item.setIcon(R.drawable.ic_baseline_dark_mode_24);
                item.setTitle(R.string.dark_mode);
            }
        }
    }
}