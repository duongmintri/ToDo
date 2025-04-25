package com.codingstuff.todolist;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.codingstuff.todolist.Model.ToDoModel;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BackupRestoreActivity extends AppCompatActivity {

    private static final String TAG = "BackupRestoreActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int CREATE_FILE_REQUEST_CODE = 101;
    private static final int OPEN_FILE_REQUEST_CODE = 102;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 103;

    private Button backupButton;
    private Button restoreButton;
    private ProgressBar progressBar;
    private TextView statusTextView;
    private FirebaseFirestore firestore;
    private FirebaseAuth mAuth;
    private String userId;
    private List<ToDoModel> taskList;

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
        } else {
            // Nếu chưa đăng nhập, quay về màn hình chính
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup_restore);

        initFirestore();
        // Thiết lập toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.backup_restore);
        }

        // Khởi tạo các thành phần UI
        backupButton = findViewById(R.id.backup_button);
        restoreButton = findViewById(R.id.restore_button);
        progressBar = findViewById(R.id.progress_bar);
        statusTextView = findViewById(R.id.status_text);

        // Khởi tạo Firestore và danh sách công việc
        taskList = new ArrayList<>();

        // Thiết lập sự kiện cho nút sao lưu
        backupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermissions()) {
                    startBackupProcess();
                }
            }
        });

        // Thiết lập sự kiện cho nút khôi phục
        restoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermissions()) {
                    openFileForRestore();
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Kiểm tra và yêu cầu quyền truy cập bộ nhớ nếu cần
     */
    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 trở lên sử dụng MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                return false;
            }
            return true;
        } else {
            // Android 10 trở xuống sử dụng READ_EXTERNAL_STORAGE và WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
                return false;
            }
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                saveBackupToFile(uri);
            }
        } else if (requestCode == OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                restoreFromFile(uri);
            }
        }
    }

    /**
     * Bắt đầu quá trình sao lưu
     */
    private void startBackupProcess() {
        progressBar.setVisibility(View.VISIBLE);
        statusTextView.setText(R.string.status_loading);

        // Tải tất cả công việc từ Firestore
        firestore.collection("users").document(userId).collection("tasks").get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        taskList.clear();

                        if (queryDocumentSnapshots.isEmpty()) {
                            progressBar.setVisibility(View.GONE);
                            statusTextView.setText(R.string.status_no_data);
                            return;
                        }

                        // Sử dụng cách tiếp cận hiệu quả hơn để lấy dữ liệu
                        for (DocumentChange documentChange : queryDocumentSnapshots.getDocumentChanges()) {
                            String id = documentChange.getDocument().getId();
                            ToDoModel toDoModel = documentChange.getDocument().toObject(ToDoModel.class).withId(id);
                            taskList.add(toDoModel);
                        }

                        // Hiển thị số lượng công việc đã tải
                        statusTextView.setText(getString(R.string.status_loaded, taskList.size()));

                        // Tạo tên file sao lưu với timestamp
                        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                        String fileName = "todolist_backup_" + timestamp + ".json";

                        // Sử dụng Storage Access Framework để lưu file
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        intent.putExtra(Intent.EXTRA_TITLE, fileName);
                        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        progressBar.setVisibility(View.GONE);
                        statusTextView.setText(getString(R.string.status_error, e.getMessage()));
                        Log.e(TAG, "Error loading data for backup", e);

                        // Kiểm tra lỗi quyền truy cập
                        if (e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED")) {
                            Toast.makeText(BackupRestoreActivity.this,
                                    getString(R.string.error_permission_denied),
                                    Toast.LENGTH_LONG).show();
                            showPermissionErrorDialog();
                        }
                    }
                });
    }

    /**
     * Lưu dữ liệu sao lưu vào file
     */
    private void saveBackupToFile(Uri uri) {
        statusTextView.setText(R.string.status_saving);

        // Sử dụng AsyncTask để tránh block UI thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Tạo JSON array cho các công việc
                    JSONArray jsonArray = new JSONArray();

                    for (ToDoModel task : taskList) {
                        JSONObject jsonTask = new JSONObject();
                        jsonTask.put("task", task.getTask());
                        jsonTask.put("due", task.getDue());
                        jsonTask.put("status", task.getStatus());
                        jsonArray.put(jsonTask);
                    }

                    // Tạo JSON object chính
                    JSONObject backupData = new JSONObject();
                    backupData.put("version", 1); // Phiên bản định dạng sao lưu
                    backupData.put("timestamp", System.currentTimeMillis());
                    backupData.put("tasks", jsonArray);
                    backupData.put("app", "TodoList");

                    // Chuyển thành chuỗi JSON với định dạng đẹp
                    String jsonString = backupData.toString(4); // Pretty print với indent 4 spaces

                    // Ghi vào file
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    if (outputStream != null) {
                        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                        BufferedWriter writer = new BufferedWriter(outputStreamWriter);
                        writer.write(jsonString);
                        writer.flush();
                        writer.close();

                        // Cập nhật UI trên main thread
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                                statusTextView.setText(getString(R.string.status_backup_success, taskList.size()));
                                Toast.makeText(BackupRestoreActivity.this, R.string.backup_success, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final IOException | JSONException e) {
                    Log.e(TAG, "Error saving backup file", e);

                    // Cập nhật UI trên main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            statusTextView.setText(getString(R.string.status_error, e.getMessage()));
                            Toast.makeText(BackupRestoreActivity.this, R.string.error_backup_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Mở file để khôi phục
     */
    private void openFileForRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, OPEN_FILE_REQUEST_CODE);
    }

    /**
     * Khôi phục dữ liệu từ file
     */
    private void restoreFromFile(Uri uri) {
        progressBar.setVisibility(View.VISIBLE);
        statusTextView.setText(R.string.status_loading);

        // Sử dụng thread riêng để đọc file
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Đọc file JSON
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream == null) {
                        throw new IOException("Không thể mở file");
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    inputStream.close();

                    final String jsonString = stringBuilder.toString();
                    final JSONObject backupData = new JSONObject(jsonString);

                    // Kiểm tra phiên bản định dạng sao lưu
                    int version = backupData.getInt("version");
                    if (version != 1) {
                        throw new JSONException("Phiên bản định dạng sao lưu không được hỗ trợ: " + version);
                    }

                    // Kiểm tra xem có phải file sao lưu của ứng dụng này không
                    if (backupData.has("app") && !"TodoList".equals(backupData.getString("app"))) {
                        throw new JSONException("File sao lưu không phải của ứng dụng này");
                    }

                    final JSONArray jsonTasks = backupData.getJSONArray("tasks");
                    final List<Map<String, Object>> tasksToRestore = new ArrayList<>();

                    for (int i = 0; i < jsonTasks.length(); i++) {
                        JSONObject jsonTask = jsonTasks.getJSONObject(i);
                        Map<String, Object> task = new HashMap<>();
                        task.put("task", jsonTask.getString("task"));
                        task.put("due", jsonTask.getString("due"));
                        task.put("status", jsonTask.getInt("status"));
                        task.put("time", FieldValue.serverTimestamp());
                        tasksToRestore.add(task);
                    }

                    // Cập nhật UI trên main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Bắt đầu khôi phục dữ liệu
                            restoreTasksToFirestore(tasksToRestore);
                        }
                    });

                } catch (final IOException | JSONException e) {
                    Log.e(TAG, "Error reading backup file", e);

                    // Cập nhật UI trên main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            statusTextView.setText(getString(R.string.status_error, e.getMessage()));
                            Toast.makeText(BackupRestoreActivity.this, R.string.error_restore_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Khôi phục các công việc vào Firestore
     */
    private void restoreTasksToFirestore(final List<Map<String, Object>> tasks) {
        statusTextView.setText(getString(R.string.status_loading));

        // Hiển thị hộp thoại xác nhận trước khi khôi phục
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_restore_title);
        builder.setMessage(getString(R.string.confirm_restore_message, tasks.size()));
        builder.setPositiveButton(R.string.confirm_restore_positive, (dialog, which) -> {
            // Thực hiện khôi phục
            performRestore(tasks);
        });
        builder.setNegativeButton(R.string.confirm_restore_negative, (dialog, which) -> {
            progressBar.setVisibility(View.GONE);
            statusTextView.setText(R.string.restore_cancelled);
        });
        builder.show();
    }

    /**
     * Thực hiện khôi phục dữ liệu sử dụng batch để tăng hiệu suất
     */
    private void performRestore(List<Map<String, Object>> tasks) {
        final int totalTasks = tasks.size();
        statusTextView.setText(getString(R.string.status_restoring, 0, totalTasks));

        // Sử dụng WriteBatch để tăng hiệu suất
        int batchSize = 20; // Giới hạn số lượng tác vụ trong một batch
        int totalBatches = (int) Math.ceil((double) tasks.size() / batchSize);
        final int[] completedBatches = {0};
        final int[] successCount = {0};
        final int[] failCount = {0};

        // Xử lý từng batch
        for (int i = 0; i < totalBatches; i++) {
            int startIndex = i * batchSize;
            int endIndex = Math.min((i + 1) * batchSize, tasks.size());
            List<Map<String, Object>> batchTasks = tasks.subList(startIndex, endIndex);

            // Tạo batch mới
            WriteBatch batch = firestore.batch();
            List<DocumentReference> refs = new ArrayList<>();

            // Thêm các tác vụ vào batch
            for (Map<String, Object> task : batchTasks) {
                DocumentReference newTaskRef = firestore.collection("users").document(userId).collection("tasks").document();
                refs.add(newTaskRef);
                batch.set(newTaskRef, task);
            }

            // Commit batch
            final int batchNumber = i + 1;
            final int batchTaskCount = batchTasks.size();

            batch.commit().addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    completedBatches[0]++;
                    successCount[0] += batchTaskCount;

                    // Cập nhật trạng thái
                    int progress = successCount[0] * 100 / totalTasks;
                    statusTextView.setText(getString(R.string.status_restoring, successCount[0], totalTasks));

                    // Kiểm tra hoàn thành
                    if (completedBatches[0] >= totalBatches) {
                        checkRestoreCompletion(successCount[0], failCount[0], totalTasks);
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    completedBatches[0]++;
                    failCount[0] += batchTaskCount;
                    Log.e(TAG, "Error restoring batch " + batchNumber, e);

                    // Kiểm tra lỗi quyền truy cập
                    if (e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED")) {
                        Toast.makeText(BackupRestoreActivity.this,
                                getString(R.string.error_permission_denied),
                                Toast.LENGTH_LONG).show();

                        // Hiển thị hướng dẫn khắc phục
                        showPermissionErrorDialog();

                        // Dừng quá trình khôi phục
                        progressBar.setVisibility(View.GONE);
                        statusTextView.setText(R.string.error_restore_stopped);
                        return;
                    }

                    // Kiểm tra hoàn thành
                    if (completedBatches[0] >= totalBatches) {
                        checkRestoreCompletion(successCount[0], failCount[0], totalTasks);
                    }
                }
            });
        }
    }

    /**
     * Hiển thị hộp thoại hướng dẫn khắc phục lỗi quyền truy cập Firestore
     */
    private void showPermissionErrorDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.error_permission_title);
        builder.setMessage(R.string.error_permission_message);
        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }

    /**
     * Kiểm tra tiến trình khôi phục
     */
    private void checkRestoreCompletion(int successCount, int failCount, int totalTasks) {
        if (successCount + failCount == totalTasks) {
            progressBar.setVisibility(View.GONE);
            String message = getString(R.string.status_restore_success, successCount, totalTasks);
            if (failCount > 0) {
                message += " (" + failCount + " lỗi)";
            }
            statusTextView.setText(message);
            Toast.makeText(this, R.string.restore_success, Toast.LENGTH_SHORT).show();

            // Refresh the main activity
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                mainActivity.onDataRefreshed();
            }

            // Add a button to return to main activity
            showReturnToMainButton();
        }
    }

    /**
     * Hiển thị nút quay lại màn hình chính
     */
    private void showReturnToMainButton() {
        Button returnButton = findViewById(R.id.return_button);
        if (returnButton != null) {
            returnButton.setVisibility(View.VISIBLE);
            returnButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }
}
