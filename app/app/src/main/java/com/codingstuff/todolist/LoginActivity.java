package com.codingstuff.todolist;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.recaptcha.Recaptcha;
import com.google.android.recaptcha.RecaptchaAction;
import com.google.android.recaptcha.RecaptchaClient;
import com.google.android.recaptcha.RecaptchaTaskResponse;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton, registerButton;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private TextView titleTextView;
    private RecaptchaClient recaptchaClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Khởi tạo Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Khởi tạo RecaptchaClient
        initRecaptcha();

        // Thiết lập toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }

        // Ánh xạ các view
        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        loginButton = findViewById(R.id.login_button);
        registerButton = findViewById(R.id.register_button);
        progressBar = findViewById(R.id.login_progress_bar);
        titleTextView = findViewById(R.id.login_title);

        // Xử lý sự kiện đăng nhập
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        // Xử lý sự kiện đăng ký
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Kiểm tra nếu người dùng đã đăng nhập (không null) và cập nhật UI tương ứng
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && !currentUser.isAnonymous()) {
            // Người dùng đã đăng nhập, chuyển đến MainActivity
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        } else {
            // Đăng nhập ẩn danh để sử dụng Firebase mà không cần xác thực
            signInAnonymously();
        }
    }

    private void initRecaptcha() {
        Recaptcha.getClient(this, "6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI")
                .addOnSuccessListener(new OnSuccessListener<RecaptchaClient>() {
                    @Override
                    public void onSuccess(RecaptchaClient client) {
                        recaptchaClient = client;
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(LoginActivity.this, "Không thể khởi tạo reCAPTCHA: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void executeRecaptchaForRegistration(final String email, final String password) {
        if (recaptchaClient == null) {
            Toast.makeText(this, "reCAPTCHA chưa sẵn sàng, vui lòng thử lại sau", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            return;
        }

        recaptchaClient.execute(RecaptchaAction.custom("signUpPassword"))
                .addOnSuccessListener(new OnSuccessListener<RecaptchaTaskResponse>() {
                    @Override
                    public void onSuccess(RecaptchaTaskResponse recaptchaTaskResponse) {
                        // Lấy token reCAPTCHA thành công, tiếp tục đăng ký
                        String recaptchaToken = recaptchaTaskResponse.getToken();
                        createNewAccount(email, password);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(LoginActivity.this, "Xác thực reCAPTCHA thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void executeRecaptchaForLogin(final String email, final String password) {
        if (recaptchaClient == null) {
            Toast.makeText(this, "reCAPTCHA chưa sẵn sàng, vui lòng thử lại sau", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            return;
        }

        recaptchaClient.execute(RecaptchaAction.custom("signInPassword"))
                .addOnSuccessListener(new OnSuccessListener<RecaptchaTaskResponse>() {
                    @Override
                    public void onSuccess(RecaptchaTaskResponse recaptchaTaskResponse) {
                        // Lấy token reCAPTCHA thành công, tiếp tục đăng nhập
                        String recaptchaToken = recaptchaTaskResponse.getToken();
                        signInWithEmailAndPassword(email, password);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(LoginActivity.this, "Xác thực reCAPTCHA thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void signInAnonymously() {
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Đăng nhập ẩn danh thành công
                            FirebaseUser user = mAuth.getCurrentUser();
                            // Không chuyển đến MainActivity, vẫn ở lại màn hình đăng nhập
                            // để người dùng có thể đăng nhập bằng tài khoản thực
                        } else {
                            // Đăng nhập ẩn danh thất bại
                            Toast.makeText(LoginActivity.this, "Không thể kết nối đến Firebase",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void loginUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Kiểm tra dữ liệu đầu vào
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email không được để trống");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Mật khẩu không được để trống");
            return;
        }

        // Hiển thị ProgressBar
        progressBar.setVisibility(View.VISIBLE);

        // Kiểm tra xem người dùng hiện tại có phải là ẩn danh không
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.isAnonymous()) {
            // Đăng xuất tài khoản ẩn danh trước khi đăng nhập
            mAuth.signOut();
        }

        // Thực hiện xác thực reCAPTCHA trước khi đăng nhập
        executeRecaptchaForLogin(email, password);
    }

    private void signInWithEmailAndPassword(String email, String password) {
        // Đăng nhập với email và password
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        progressBar.setVisibility(View.GONE);
                        if (task.isSuccessful()) {
                            // Đăng nhập thành công, cập nhật UI với thông tin của người dùng đã đăng nhập
                            Toast.makeText(LoginActivity.this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // Nếu đăng nhập thất bại, hiển thị thông báo cho người dùng
                            String errorMessage = "Lỗi không xác định";
                            if (task.getException() != null) {
                                Exception exception = task.getException();
                                errorMessage = exception.getMessage();

                                // Xử lý các loại lỗi cụ thể
                                if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                                    errorMessage = "Email hoặc mật khẩu không đúng";
                                }
                            }
                            Toast.makeText(LoginActivity.this, "Đăng nhập thất bại: " + errorMessage, Toast.LENGTH_SHORT).show();

                            // Đăng nhập lại ẩn danh để có thể sử dụng Firebase
                            signInAnonymously();
                        }
                    }
                });
    }

    private void registerUser() {
        final String email = emailEditText.getText().toString().trim();
        final String password = passwordEditText.getText().toString().trim();

        // Kiểm tra dữ liệu đầu vào
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email không được để trống");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Mật khẩu không được để trống");
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError("Mật khẩu phải có ít nhất 6 ký tự");
            return;
        }

        // Hiển thị ProgressBar
        progressBar.setVisibility(View.VISIBLE);

        // Kiểm tra xem người dùng hiện tại có phải là ẩn danh không
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.isAnonymous()) {
            // Nâng cấp tài khoản ẩn danh lên tài khoản email/password
            linkAnonymousAccountWithEmail(email, password);
        } else {
            // Thực hiện xác thực reCAPTCHA trước khi đăng ký
            executeRecaptchaForRegistration(email, password);
        }
    }

    private void linkAnonymousAccountWithEmail(String email, String password) {
        // Lấy người dùng hiện tại
        FirebaseUser user = mAuth.getCurrentUser();

        // Tạo thông tin đăng nhập email/password
        AuthCredential credential = EmailAuthProvider.getCredential(email, password);

        // Liên kết tài khoản ẩn danh với thông tin đăng nhập
        user.linkWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        progressBar.setVisibility(View.GONE);
                        if (task.isSuccessful()) {
                            // Liên kết thành công
                            Toast.makeText(LoginActivity.this, "Đăng ký thành công", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // Liên kết thất bại
                            String errorMessage = "Lỗi không xác định";
                            if (task.getException() != null) {
                                Exception exception = task.getException();
                                errorMessage = exception.getMessage();

                                // Xử lý các loại lỗi cụ thể
                                if (exception instanceof FirebaseAuthUserCollisionException) {
                                    errorMessage = "Email này đã được sử dụng";
                                } else if (exception instanceof FirebaseAuthWeakPasswordException) {
                                    errorMessage = "Mật khẩu quá yếu";
                                } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                                    errorMessage = "Email không hợp lệ";
                                }
                            }
                            Toast.makeText(LoginActivity.this, "Đăng ký thất bại: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void createNewAccount(String email, String password) {
        // Tạo tài khoản mới
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        progressBar.setVisibility(View.GONE);
                        if (task.isSuccessful()) {
                            // Đăng ký thành công
                            Toast.makeText(LoginActivity.this, "Đăng ký thành công", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // Đăng ký thất bại
                            String errorMessage = "Lỗi không xác định";
                            if (task.getException() != null) {
                                Exception exception = task.getException();
                                errorMessage = exception.getMessage();

                                // Xử lý các loại lỗi cụ thể
                                if (exception instanceof FirebaseAuthUserCollisionException) {
                                    errorMessage = "Email này đã được sử dụng";
                                } else if (exception instanceof FirebaseAuthWeakPasswordException) {
                                    errorMessage = "Mật khẩu quá yếu";
                                } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                                    errorMessage = "Email không hợp lệ";
                                }
                            }
                            Toast.makeText(LoginActivity.this, "Đăng ký thất bại: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
