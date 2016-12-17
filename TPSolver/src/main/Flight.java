package main;

/**
 * Created by ivababukova on 12/16/16.
 */
public class Flight {

    public int id;
    public Airport dep;
    public Airport arr;
    public float date;
    public float duration;
    public float cost;

    public Flight(int id,
                  Airport dep,
                  Airport arr,
                  float date,
                  float duration,
                  float cost){
        this.id = id;
        this.dep = dep;
        this.arr = arr;
        this.date = date;
        this.duration = duration;
        this.cost = cost;
    }

}
