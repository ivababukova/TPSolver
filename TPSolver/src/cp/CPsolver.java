package cp;

import java.util.ArrayList;

import main.Airport;
import main.Flight;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.*;
import org.chocosolver.solver.variables.*;

import helpers.HelperMethods;
import helpers.Tuple;

/**
 * Created by ivababukova on 12/16/16.
 * this is the CP solver for TP
 */
public class CPsolver {

    private ArrayList<Flight> flights;
    private HelperMethods h;
    private int T;
    private int B; // upper bound on the cost
    private String[] args;
    ArrayList<Tuple> tuples; // an array of (airport a, date d) that means that traveller must be at a at time d

    private Model model;
    private Solver solver;
    private IntVar[] S; // the flights schedule
    private IntVar[] D; // for hard constraint 2
    private IntVar z; // the number of flights in the schedule
    private IntVar[] C; // C[i] is equal to the cost of flight S[i]
    private IntVar cost_sum; // the total cost of the flights schedule
    private IntVar last_flight; // the day when traveller will have finished the trip
    private IntVar trip_duration;

    public CPsolver(
            ArrayList<Airport> as,
            ArrayList<Flight> fs,
            int T,
            int B,
            String[] args,
            ArrayList<Tuple> tuples
    ){
        this.flights = fs;
        this.T = T;
        this.B = B;
        this.h = new HelperMethods(as, fs);
        this.args = args;
        this.tuples = tuples;
    }


    // filters out flights that won't arrive within the specified travel time
    private void filtering() {
        ArrayList<Flight> newflights = new ArrayList<>();
        for(Flight f: this.flights) {
            if (f.date + f.duration <= this.T) newflights.add(f);
        }
        this.flights = newflights;
    }

    private void init(){
        this.model = new Model("TP CPsolver");
        this.S = model.intVarArray("Flights Schedule", flights.size() + 1, 0, flights.size());
        this.z = model.intVar("End of schedule", 2, flights.size());
        this.C = this.model.intVarArray("The cost of each taken flight", flights.size() + 1, 0, 500);
        this.cost_sum = this.model.intVar(0, B);
        this.last_flight = this.model.intVar(1, flights.size());
        this.trip_duration = this.model.intVar(1, T);

        this.solver = model.getSolver();

        this.model.sum(C, "=", cost_sum).post();

        this.findSchedule();

    }

    private void findSchedule() {
        Airport a0 = h.getHomePoint();
        int[] to_home = h.arrayToint(h.allToHome(a0, this.T));
        int[] from_home = h.arrayToint(h.allFrom(a0));

        model.member(S[0], from_home).post();
        this.model.arithm(S[1], "!=", 0).post();
        this.model.arithm(C[0], "!=", 0).post();

        destinationConstraint();
//        if (this.tuples != null) hardConstraint2();

        for(int i = 1; i <= flights.size(); i++) {
            Flight f = h.getFlightByID(i);

            timeConstraint(f);

            // if the sequence ends at i, then s[i] must be 0
            model.ifThen(
                    model.arithm(z, "=", i),
                    model.and(
                            model.arithm(S[i], "=", 0),
                            model.member(S[i-1], to_home),
                            model.arithm(last_flight, "=", S[i-1])
                    )
            );

            model.ifThen(
                    model.arithm(z, ">", i),
                    model.arithm(S[i], "!=", 0)
            );

            // if s[i-1] is 0, then s[i] must be 0
            model.ifThen(
                    model.arithm(S[i-1], "=", 0),
                    model.arithm(S[i], "=", 0)
            );

            this.model.ifThen(
                    model.arithm(S[i], "=", 0),
                    model.arithm(C[i], "=", 0)
            );

            this.model.ifThen(
                    model.arithm(S[i], "!=", 0),
                    model.arithm(C[i], "!=", 0)
            );

            // if s[i] is not 0, then s[i-1] must not be 0
            model.ifThen(
                    model.arithm(S[i], "!=", 0),
                    model.arithm(S[i-1], "!=", 0)
            );

            model.ifThen(
                    model.arithm(last_flight, "=", i),
                    model.arithm(trip_duration, "=", (int) (f.date + f.duration))
            );
        }
        costConstraint();
        this.model.allDifferentExcept0(S).post();
    }

    // call this function when no flights to connection airports are allowed
    private void removeConnections(){
        ArrayList<Flight> newflights = new ArrayList<>();
        for(Flight f: this.flights) {
            if (!f.arr.purpose.equals("connection") && !f.dep.purpose.equals("connection")) newflights.add(f);
        }
        this.flights = newflights;
        for (Flight f: newflights) {
            System.out.print(f.dep.name + f.arr.name + " ");
        }
        System.out.println();
    }

    /*** HARD CONSTRAINT 2 CODE ***/

    private void hardConstraint2(){
        this.D = model.intVarArray(
                "Destinations with hard constr 2",
                tuples.size() + 1,
                0,
                flights.size());
        this.model.arithm(D[0], "=", 0).post(); // D[0] is not important
        this.model.allDifferent(D).post();
        int index = 1;
        for (Tuple tup : this.tuples) {
            Airport a = tup.getA();
            a.setIndex(index);
            double date = tup.getDate();
            this.dateLocationConstraint(a, date, index);
            index ++;
        }
    }

    // hard constraint 2
    private void dateLocationConstraint(Airport a, double date, int index) {
        System.out.println(a.name + " " + date + " " + index);
        int[] all_to_before = h.arrayToint(h.allToBefore(a, date)); // all flights to desired destination
        int[] all_from_after = h.arrayToint(h.allFromAfter(a, date)); // all flights from desired destination

        for(int j = 1; j <= flights.size(); j++) {
            model.ifThen(
                    model.arithm(D[index], "=", j),
                    model.and(
                            model.member(S[j-1], all_to_before),
                            model.member(S[j], all_from_after)
                    )
            );
        }
    }

    /*** ***/

    // all destinations must be visited
    private void destinationConstraint(){
        for (Airport d: h.getDestinations()) {
            int[] all_to = h.arrayToint(h.allTo(d)); // all flights that fly to d
            IntVar X = this.model.intVar(1, all_to.length);
            this.model.among(X, S, all_to).post(); // S must contain at least one flight that goes to d
        }
    }

    // todo this is very inefficient
    // set the values of C
    private void costConstraint(){
        for(int i = 1; i<=flights.size(); i++) {
            int cost = (int) h.getFlightByID(i).cost;
            for (int j = 0; j <= flights.size(); j++) {
                this.model.ifThen(
                        model.arithm(S[j], "=", i),
                        model.arithm(C[j], "=", cost)
                );
            }
        }
    }

    // enforces both time and departure constraints at the same time for all flights but the last flight
    private void timeConstraint(Flight f){
        ArrayList<Integer> af = h.allFromTimed(f.arr, f, f.arr.conn_time);
        int[] all_from = h.arrayToint(af);
        for (int j = 1; j <= flights.size(); j++) {
            model.ifThen(
                    model.and(model.arithm(S[j-1],"=", f.id), model.arithm(z, "!=", j)),
                    model.member(S[j], all_from)
            );
        }
    }

    public void getSolution(){
        init();
        Solution x;
        Boolean m = null;
        IntVar to_optimise = null;

        if (args.length == 0) {
            x = solver.findSolution();
            if (x == null) {
                System.out.println("No solution was found");
                return;
            }
            printSolution(x);
            return;
        }

        if (args.length == 2){

            if (args[0].equals("-min")) {
                System.out.print("Solution with minimum ");
                m = Model.MINIMIZE;
            } else if (args[0].equals("-max")) {
                System.out.print("Solution with maximum ");
                m = Model.MAXIMIZE;
            } else {
                System.out.println("Wrong first argument provided");
                return;
            }

            if (args[1].equals("-cost")) {
                System.out.println("cost:");
                to_optimise = this.cost_sum;
            } else if (args[1].equals("-flights")) {
                System.out.println("number of flights:");
                to_optimise = this.z;
            } else if (args[1].equals("-trip_duration")){
                System.out.println("trip duration:");
                to_optimise = this.trip_duration;
            } else {
                System.out.println("Wrong second argument provided");
                return;
            }

            x = solver.findOptimalSolution(to_optimise, m);
            if (x == null) {
                System.out.println("No solution was found");
                return;
            }
            printSolution(x);
        }

        else {
            System.out.println("Not enough arguments provided");
        }
    }

    private void printSolution(Solution x) {
        System.out.println("z is: " + x.getIntVal(z));
        for (int i = 0; i < x.getIntVal(z); i++) {
            System.out.print(h.getFlightByID(x.getIntVal(S[i])).dep.name);
            System.out.print(h.getFlightByID(x.getIntVal(S[i])).arr.name + " ");
        }
        System.out.println();
        for (int i = 0; i < x.getIntVal(z); i++) {
            int date = (int) h.getFlightByID(x.getIntVal(S[i])).date;
            if (date <= 9) System.out.print(date + "  ");
            if (date > 9) System.out.print(date + " ");
        }
        System.out.println();
        for (int i = 0; i < x.getIntVal(z); i++) {
            System.out.print(x.getIntVal(C[i]) + " ");
        }
        System.out.println("\nLast flight: " + x.getIntVal(last_flight));
        System.out.println("Total cost: " + x.getIntVal(cost_sum));
        System.out.println("Trip duration: " + x.getIntVal(trip_duration));
    }

}