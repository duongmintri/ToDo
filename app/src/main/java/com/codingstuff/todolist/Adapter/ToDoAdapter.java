package com.codingstuff.todolist.Adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.codingstuff.todolist.AddNewTask;
import com.codingstuff.todolist.MainActivity;
import com.codingstuff.todolist.Model.ToDoModel;
import com.codingstuff.todolist.R;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class ToDoAdapter extends RecyclerView.Adapter<ToDoAdapter.MyViewHolder> {

    private List<ToDoModel> todoList;
    private Context context;
    private FirebaseFirestore firestore;
    private TaskActionListener actionListener;

    // Interface cho các hành động trên task
    public interface TaskActionListener {
        void onEditTask(ToDoModel task);
        void onDeleteTask(int position);
    }

    // Constructor cho MainActivity
    public ToDoAdapter(MainActivity mainActivity, List<ToDoModel> todoList) {
        this.todoList = todoList;
        this.context = mainActivity;
        this.actionListener = new TaskActionListener() {
            @Override
            public void onEditTask(ToDoModel task) {
                Bundle bundle = new Bundle();
                bundle.putString("task", task.getTask());
                bundle.putString("due", task.getDue());
                bundle.putString("id", task.TaskId);

                AddNewTask addNewTask = new AddNewTask();
                addNewTask.setArguments(bundle);
                addNewTask.show(mainActivity.getSupportFragmentManager(), AddNewTask.TAG);
            }

            @Override
            public void onDeleteTask(int position) {
                ToDoModel toDoModel = todoList.get(position);
                FirebaseFirestore.getInstance().collection("task").document(toDoModel.TaskId).delete();
                todoList.remove(position);
                notifyItemRemoved(position);
            }
        };
    }

    // Constructor cho các Activity khác
    public ToDoAdapter(TaskActionListener listener, List<ToDoModel> todoList) {
        this.todoList = todoList;
        this.context = null; // Sẽ được gán trong onCreateViewHolder
        this.actionListener = listener;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (context == null) {
            context = parent.getContext();
        }
        View view = LayoutInflater.from(context).inflate(R.layout.each_task, parent, false);
        firestore = FirebaseFirestore.getInstance();

        return new MyViewHolder(view);
    }

    public void deleteTask(int position) {
        if (actionListener != null) {
            actionListener.onDeleteTask(position);
        }
    }

    public Context getContext() {
        return context;
    }

    public void editTask(int position) {
        if (actionListener != null) {
            actionListener.onEditTask(todoList.get(position));
        }
    }
    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {

        ToDoModel toDoModel = todoList.get(position);
        holder.mCheckBox.setText(toDoModel.getTask());

        holder.mDueDateTv.setText("Hạn: " + toDoModel.getDue());

        boolean isCompleted = toBoolean(toDoModel.getStatus());
        holder.mCheckBox.setChecked(isCompleted);

        // Thay đổi màu nền của card khi hoàn thành
        try {
            MaterialCardView cardView = holder.itemView.findViewById(R.id.task_card);
            if (cardView != null) {
                TypedValue typedValue = new TypedValue();
                if (isCompleted) {
                    context.getTheme().resolveAttribute(R.attr.colorTaskCompletedBackground, typedValue, true);
                    cardView.setCardBackgroundColor(typedValue.data);
                } else {
                    context.getTheme().resolveAttribute(R.attr.colorTaskBackground, typedValue, true);
                    cardView.setCardBackgroundColor(typedValue.data);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        holder.mCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    if (isChecked){
                        firestore.collection("task").document(toDoModel.TaskId).update("status" , 1);
                        MaterialCardView cardView = holder.itemView.findViewById(R.id.task_card);
                        if (cardView != null) {
                            TypedValue typedValue = new TypedValue();
                            context.getTheme().resolveAttribute(R.attr.colorTaskCompletedBackground, typedValue, true);
                            cardView.setCardBackgroundColor(typedValue.data);
                        }
                    }else{
                        firestore.collection("task").document(toDoModel.TaskId).update("status" , 0);
                        MaterialCardView cardView = holder.itemView.findViewById(R.id.task_card);
                        if (cardView != null) {
                            TypedValue typedValue = new TypedValue();
                            context.getTheme().resolveAttribute(R.attr.colorTaskBackground, typedValue, true);
                            cardView.setCardBackgroundColor(typedValue.data);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }

    private boolean toBoolean(int status){
        return status != 0;
    }

    @Override
    public int getItemCount() {
        return todoList.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder{

        TextView mDueDateTv;
        CheckBox mCheckBox;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);

            mDueDateTv = itemView.findViewById(R.id.due_date_tv);
            mCheckBox = itemView.findViewById(R.id.mcheckbox);

        }
    }
}
