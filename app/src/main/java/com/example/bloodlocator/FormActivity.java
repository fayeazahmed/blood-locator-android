package com.example.bloodlocator;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.firebase.firestore.GeoPoint;

import java.util.HashMap;
import java.util.Map;

public class FormActivity extends AppCompatActivity implements View.OnClickListener {

    private Location coordinates;
    private EditText formName;
    private EditText formAddress;
    private EditText formContact;
    private EditText formPassword;
    private Spinner spinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.form_add);

        coordinates = getIntent().getParcelableExtra("coordinates");
        formName = findViewById(R.id.formName);
        formAddress = findViewById(R.id.formAddress);
        formContact = findViewById(R.id.formContact);
        formPassword = findViewById(R.id.formPassword);

        spinner = findViewById(R.id.formSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getApplicationContext(),
                R.array.blood_groups, R.layout.spinner_text);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        Button submitBtn = findViewById(R.id.submitBtn);
        submitBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if(!formName.getText().toString().isEmpty() && !formAddress.getText().toString().isEmpty() && !formContact.getText().toString().isEmpty() && !formPassword.getText().toString().isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("name", formName.getText().toString());
            data.put("address", formAddress.getText().toString());
            data.put("contact", formContact.getText().toString());
            data.put("password", formPassword.getText().toString());
            data.put("bloodGroup", spinner.getSelectedItem().toString());
            data.put("coordinates", new GeoPoint(coordinates.getLatitude(), coordinates.getLongitude()));

            formName.setText("");
            formAddress.setText("");
            formContact.setText("");
            formPassword.setText("");

            Database.addDonor(data, (success, doc) -> {
                Intent intent = getIntent().putExtra("result", success);
                intent.putExtra("bloodGroup", doc.getString("bloodGroup"));
                setResult(RESULT_OK, intent);
                finish();
            });
        } else {
            Toast.makeText(this, "Fill out all the fields", Toast.LENGTH_SHORT).show();
        }
    }
}