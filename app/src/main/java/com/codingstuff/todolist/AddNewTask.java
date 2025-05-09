package com.codingstuff.todolist;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AddNewTask  extends BottomSheetDialogFragment {

    public static final String TAG = "AddNewTask";

    private TextView setDueDate;
    private EditText mTaskEdit;
    private Button mSaveBtn;
    private FirebaseFirestore firestore;
    private FirebaseAuth mAuth;
    private Context context;
    private String dueDate = "";
    private String id = "";
    private String dueDateUpdate = "";
    private String userId = "";

    public static AddNewTask newInstance(){
        return new AddNewTask();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.add_new_task , container , false);

    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            setDueDate = view.findViewById(R.id.set_due_tv);
            mTaskEdit = view.findViewById(R.id.task_edittext);
            mSaveBtn = view.findViewById(R.id.save_btn);

            firestore = FirebaseFirestore.getInstance();
            mAuth = FirebaseAuth.getInstance();

            // Lấy userId của người dùng hiện tại
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
            } else {
                // Nếu chưa đăng nhập, đóng dialog
                dismiss();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        boolean isUpdate = false;

        final Bundle bundle = getArguments();
        if (bundle != null){
            isUpdate = true;
            String task = bundle.getString("task");
            id = bundle.getString("id");
            dueDateUpdate = bundle.getString("due");

            mTaskEdit.setText(task);
            setDueDate.setText(dueDateUpdate);

            if (task.length() > 0){
                mSaveBtn.setEnabled(false);
                mSaveBtn.setBackgroundColor(Color.GRAY);
            }
        }

        mTaskEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
               if (s.toString().equals("")){
                   mSaveBtn.setEnabled(false);
                   mSaveBtn.setBackgroundColor(Color.GRAY);
               }else{
                   mSaveBtn.setEnabled(true);
                   mSaveBtn.setBackgroundColor(getResources().getColor(R.color.green_blue));
               }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        setDueDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Calendar calendar = Calendar.getInstance();

                    int MONTH = calendar.get(Calendar.MONTH);
                    int YEAR = calendar.get(Calendar.YEAR);
                    int DAY = calendar.get(Calendar.DATE);

                    DatePickerDialog datePickerDialog = new DatePickerDialog(context, new DatePickerDialog.OnDateSetListener() {
                        @Override
                        public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                            try {
                                month = month + 1;
                                String formattedDate = String.format("%02d/%02d/%d", dayOfMonth, month, year);
                                setDueDate.setText("Chọn ngày hết hạn: " + formattedDate);
                                dueDate = formattedDate;
                            } catch (Exception e) {
                                e.printStackTrace();
                                dueDate = dayOfMonth + "/" + month + "/" + year;
                                setDueDate.setText("Chọn ngày hết hạn: " + dueDate);
                            }
                        }
                    } , YEAR , MONTH , DAY);

                    datePickerDialog.getDatePicker().setMinDate(calendar.getTimeInMillis()); // Không cho phép chọn ngày trong quá khứ
                    datePickerDialog.show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(context, "Lỗi khi mở lịch", Toast.LENGTH_SHORT).show();
                }
            }
        });

        boolean finalIsUpdate = isUpdate;
        mSaveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String task = mTaskEdit.getText().toString();

                    if (finalIsUpdate){
                        if (dueDate == null || dueDate.isEmpty()) {
                            dueDate = dueDateUpdate != null ? dueDateUpdate : "";
                        }
                        firestore.collection("users").document(userId).collection("tasks").document(id).update("task" , task , "due" , dueDate);
                        Toast.makeText(context, "Công việc đã được cập nhật", Toast.LENGTH_SHORT).show();

                    }
                    else {
                        if (task.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập nội dung công việc!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (dueDate == null || dueDate.isEmpty()) {
                            Toast.makeText(context, "Vui lòng chọn ngày hết hạn!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Map<String, Object> taskMap = new HashMap<>();

                        taskMap.put("task", task);
                        taskMap.put("due", dueDate);
                        taskMap.put("status", 0);
                        taskMap.put("time", FieldValue.serverTimestamp());

                        firestore.collection("users").document(userId).collection("tasks").add(taskMap).addOnCompleteListener(new OnCompleteListener<DocumentReference>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentReference> task) {
                                try {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(context, "Đã thêm công việc mới", Toast.LENGTH_SHORT).show();
                                    } else {
                                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Lỗi không xác định";
                                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                try {
                                    Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }
                        });
                    }
                    dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        Toast.makeText(context, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        Activity activity = getActivity();
        if (activity instanceof  OnDialogCloseListner){
            ((OnDialogCloseListner)activity).onDialogClose(dialog);
        }
    }
}
