package com.codingstuff.todolist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.codingstuff.todolist.Adapter.ToDoAdapter;
import com.codingstuff.todolist.Model.ToDoModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;

import org.threeten.bp.LocalDate;
import org.threeten.bp.format.DateTimeFormatter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CalendarActivity extends AppCompatActivity implements OnDialogCloseListner {

    private MaterialCalendarView calendarView;
    private RecyclerView recyclerView;
    private FloatingActionButton addTaskFab;
    private TextView selectedDateText;
    private TextView noTasksText;
    private Toolbar toolbar;

    private FirebaseFirestore firestore;
    private ToDoAdapter adapter;
    private List<ToDoModel> mList;
    private List<ToDoModel> allTasksList;
    private DateTimeFormatter dateFormatter;
    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        try {
            // Thiết lập toolbar
            toolbar = findViewById(R.id.calendar_toolbar);
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }

            // Khởi tạo các view
            calendarView = findViewById(R.id.calendarView);
            recyclerView = findViewById(R.id.calendar_recycler_view);
            addTaskFab = findViewById(R.id.calendar_add_task_fab);
            selectedDateText = findViewById(R.id.selected_date_text);
            noTasksText = findViewById(R.id.no_tasks_calendar_text);

            // Khởi tạo Firestore
            firestore = FirebaseFirestore.getInstance();

            // Thiết lập RecyclerView
            recyclerView.setHasFixedSize(true);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

            // Khởi tạo danh sách và adapter
            mList = new ArrayList<>();
            allTasksList = new ArrayList<>();

            // Tạo adapter với context của CalendarActivity
            adapter = new ToDoAdapter(new ToDoAdapter.TaskActionListener() {
                @Override
                public void onEditTask(ToDoModel task) {
                    // Xử lý sự kiện chỉnh sửa công việc
                    Bundle bundle = new Bundle();
                    bundle.putString("task", task.getTask());
                    bundle.putString("due", task.getDue());
                    bundle.putString("id", task.TaskId);

                    AddNewTask addNewTask = new AddNewTask();
                    addNewTask.setArguments(bundle);
                    addNewTask.show(getSupportFragmentManager(), AddNewTask.TAG);
                }

                @Override
                public void onDeleteTask(int position) {
                    // Xử lý sự kiện xóa công việc
                    ToDoModel toDoModel = mList.get(position);
                    firestore.collection("task").document(toDoModel.TaskId).delete();
                    mList.remove(position);
                    adapter.notifyItemRemoved(position);
                    loadAllTasks(); // Tải lại dữ liệu sau khi xóa
                }
            }, mList);
            recyclerView.setAdapter(adapter);

            // Định dạng ngày tháng
            dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            // Thiết lập ngày hiện tại
            CalendarDay today = CalendarDay.today();
            selectedDate = today.getDate().format(dateFormatter);
            updateSelectedDateText(today);

            // Tải tất cả công việc từ Firestore
            loadAllTasks();

            // Xử lý sự kiện chọn ngày
            calendarView.setOnDateChangedListener(new OnDateSelectedListener() {
                @Override
                public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
                    if (selected) {
                        selectedDate = date.getDate().format(dateFormatter);
                        updateSelectedDateText(date);
                        filterTasksByDate(selectedDate);
                    }
                }
            });

            // Xử lý sự kiện thêm công việc mới
            addTaskFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AddNewTask.newInstance().show(getSupportFragmentManager(), AddNewTask.TAG);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateSelectedDateText(CalendarDay date) {
        LocalDate localDate = date.getDate();

        String[] dayNames = new String[]{"Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"};
        int dayOfWeek = localDate.getDayOfWeek().getValue() % 7; // Chuyển từ 1-7 (Thứ hai - Chủ nhật) sang 0-6 (Chủ nhật - Thứ bảy)

        String dateStr = String.format("%s, %02d/%02d/%d",
                dayNames[dayOfWeek],
                localDate.getDayOfMonth(),
                localDate.getMonthValue(),
                localDate.getYear());

        selectedDateText.setText("Công việc ngày " + dateStr);
    }

    private void loadAllTasks() {
        try {
            Query query = firestore.collection("task").orderBy("time", Query.Direction.DESCENDING);
            query.addSnapshotListener(new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                    if (error != null) {
                        return;
                    }

                    if (value == null) {
                        return;
                    }

                    try {
                        allTasksList.clear();
                        for (DocumentChange documentChange : value.getDocumentChanges()) {
                            if (documentChange.getType() == DocumentChange.Type.ADDED) {
                                String id = documentChange.getDocument().getId();
                                ToDoModel toDoModel = documentChange.getDocument().toObject(ToDoModel.class).withId(id);
                                allTasksList.add(toDoModel);
                            }
                        }

                        // Đánh dấu các ngày có công việc trên lịch
                        markDatesWithEvents();

                        // Lọc công việc theo ngày đã chọn
                        filterTasksByDate(selectedDate);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void markDatesWithEvents() {
        try {
            // Xóa tất cả decorators hiện tại
            calendarView.removeDecorators();

            // Tạo tập hợp các ngày có công việc
            Set<CalendarDay> eventDays = new HashSet<>();

            for (ToDoModel task : allTasksList) {
                if (task.getDue() != null && !task.getDue().isEmpty()) {
                    try {
                        // Chuyển đổi chuỗi ngày thành đối tượng LocalDate
                        LocalDate date = LocalDate.parse(task.getDue(), dateFormatter);
                        if (date != null) {
                            CalendarDay day = CalendarDay.from(date);

                            eventDays.add(day);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            // Thêm decorator cho các ngày có công việc
            if (!eventDays.isEmpty()) {
                EventDecorator eventDecorator = new EventDecorator(
                        getResources().getColor(R.color.green_blue), eventDays);
                calendarView.addDecorator(eventDecorator);
            }

            // Đánh dấu ngày hiện tại
            CalendarDay today = CalendarDay.today();
            Set<CalendarDay> todaySet = new HashSet<>();
            todaySet.add(today);
            EventDecorator todayDecorator = new EventDecorator(
                    getResources().getColor(R.color.dark_blue), todaySet);
            calendarView.addDecorator(todayDecorator);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void filterTasksByDate(String date) {
        try {
            mList.clear();
            for (ToDoModel task : allTasksList) {
                if (task.getDue() != null && task.getDue().equals(date)) {
                    mList.add(task);
                }
            }

            // Cập nhật hiển thị
            adapter.notifyDataSetChanged();

            // Hiển thị thông báo khi không có công việc
            if (mList.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                noTasksText.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                noTasksText.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDialogClose(DialogInterface dialogInterface) {
        // Tải lại tất cả công việc khi có thay đổi
        loadAllTasks();
    }
}
