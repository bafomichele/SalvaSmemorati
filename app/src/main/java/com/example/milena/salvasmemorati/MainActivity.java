package com.example.milena.salvasmemorati;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.places.Places;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {
    //  Lista che deve contenere i task da svolgere
    protected static List<Task> taskList;
    //  L'istanza per le API Google deve essere globale
    protected static GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Inizializza le API di Google e richiede i servizi: GEO_DATA e PLACE_DETECTION
        mGoogleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .enableAutoManage(this, this)
                .build();
        //  Recupera la lista dei task salvati
        getLists();
        //  Avvia il servizio di ricerca luoghi
        Intent intent = new Intent(this, MyService.class);
        startService(intent);
    }

    //  Metodo che ricrea la lista dei task a partire dai dati salvati
    void getLists() {
        //  Accede alle preferenze
        SharedPreferences savedTasks = getPreferences(Context.MODE_PRIVATE);
        //  Inizializza la lista di sessione che conterrà i task
        taskList = new ArrayList<>();
        //  Il contatore serve a generare le chiavi
        int counter = 0;
        //  Finché ci sono task salvati
        while (savedTasks.contains("name" + counter)) {
            //  li trasforma in oggetti Task
            Task task = new Task(savedTasks.getString("name" + counter, "Non Trovato"),
                                savedTasks.getString("cat" + counter, "Sconosciuto"));
            task.setStatus(savedTasks.getBoolean("status" + counter, false));
            //  li aggiunge alla lista di sessione e alla View
            taskList.add(task);
            addRow(task);
            counter++;
        }
        //  Per evitare inconsistenze tra lista di sessione e dati salvati, questi vengono svuotati
        getPreferences(Context.MODE_PRIVATE).edit().clear().commit();
    }

    //  Necessario per implementare le API Google
    //  In caso di mancata connessione, mostra semplicemente una notifica toast
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(getApplicationContext(), "Connessione ai servizi Google non riuscita", Toast.LENGTH_SHORT).show();
    }

    //  Salva i Task nelle Preferences alla chiusura dell'activity
    @Override
    protected void onSaveInstanceState (Bundle outState){
        super.onSaveInstanceState(outState);
        // Accede al file delle preferenze e riceve l'editor
        SharedPreferences.Editor taskEditor = getPreferences(Context.MODE_PRIVATE).edit();
        //  Svuota le sharedPreferences: è necessario per la consistenza dei dati, in quanto Android
        //  non garantisce che questo metodo venga chiamato solo alla chiusura dell'activity
        //taskEditor.clear().commit();
        //  L'indice serve per generare le chiavi
        int index = 0;
        //  Per ciascun task della lista
        for (Task task : taskList) {
            // Aggiungi il nuovo task e il suo stato, memorizzandone l'indice nelle chiavi
            taskEditor.putString("name" + index, task.getD());
            taskEditor.putBoolean("status" + index, task.getS());
            taskEditor.putString("cat" + index, task.getT());
            index++;
        }
        taskEditor.apply();
    }

    //  Costruisce un nuovo Task e lo aggiunge alle liste - Viene chiamato alla pressione del bottone Salva
    void newTask(View view) {
        // Salva la descrizione del promemoria e il tipo di attività
        EditText editText = (EditText) findViewById(R.id.reminder_text);
        String promemoria = editText.getText().toString();
        Spinner spinner = (Spinner) findViewById(R.id.task_type);
        String tipologia = spinner.getSelectedItem().toString();
        //  Crea un nuovo Task con i dati inseriti
        Task task = new Task(promemoria, tipologia);
        //  Aggiunge il task alla lista di sessione
        taskList.add(task);
        //  Aggiorna la lista e la visualizza
        addRow(task);
    }

    //  Aggiunge una riga alla lista visualizzata
    //  Viene chiamato alla creazione di un nuovo task e al ripristino di quelli salvati
    void addRow(Task task) {
        //  Crea una nuova view in base al layout personalizzato "items"
        LinearLayout viewList = (LinearLayout) findViewById(R.id.task_list);
        View child = getLayoutInflater().inflate(R.layout.items, null);
        //  Riempie la view con tutte le informazioni del task
        TextView description = (TextView) child.findViewById(R.id.reminder_description);
        description.setText(task.getD());
        TextView type = (TextView) child.findViewById(R.id.task_category);
        type.setText(task.getT());
        //  Imposta il task come attivo e inizializza un listener sullo stato dello switch
        Switch active = (Switch) child.findViewById(R.id.active);
        active.setChecked(task.getS());
        //  Associa il task a un tag dello switch
        active.setTag(task);
        //  Crea un riferimento final al task per utilizzarlo nel listener
        final Task target = task;
        //  Imposta il listener, si usa una classe anonima per personalizzare il metodo onCheckedChange
        active.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                target.setStatus(isChecked);
            }
        });
        //  Imposta un bottone per eliminare il task
        Button delete = (Button) child.findViewById(R.id.delete_row);
        //  Associa il task a un tag del bottone
        delete.setTag(task);
        //  Visualizza la riga del task
        viewList.addView(child);
    }

    //  Elimina il Task sia dalla lista di sessione che dalla lista visualizzata
    void deleteRow(View view) {
        System.out.print("Lista prima dell'eliminazione");
        for (Task task : taskList) {
            System.out.print(task.getD() + task.getT());
        }
        //  Rimuove il task dalla lista
        Task daEliminare = (Task) view.getTag();
        System.out.print("Task da cancellare " + daEliminare.getD() + daEliminare.getT());
        taskList.remove(view.getTag());
        System.out.print("Lista dopo l'eliminazione");
        for (Task task : taskList) {
            System.out.print(task.getD() + task.getT());
        }
        //  Identifica la riga da rimuovere (il parent del button)
        ViewGroup row = (ViewGroup) view.getParent();
        //  Rimuove la riga risalendo al suo parent
        ViewGroup parent = (ViewGroup) row.getParent();
        parent.removeView(row);
    }
}

//  Classe che contiene le informazioni di un task
class Task {
    //  Le informazioni di un task sono 3: testo del promemoria, tipo di task e se è attivato o meno
    private String description;
    private String type;
    private boolean status = true;

    //  Costruttore (non viene chiesto lo stato, ogni nuovo task è attivo di default)
    public Task (String desc, String cat){
        description = desc;
        type = cat;
    }

    //  Getter
    public String getD() {
        return description;
    }
    public String getT() { return type; }
    public boolean getS() { return status; }

    //  Setter dello stato: non è implementata la modifica del testo del promemoria, nè del tipo di task
    public void setStatus(boolean active){
        status = active;
    }
}