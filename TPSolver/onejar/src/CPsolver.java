import java.util.ArrayList;
import java.util.List;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.*;
import org.chocosolver.solver.variables.*;

/**
 * Created by ivababukova on 12/16/16.
 * this is the CP solver for TP
 */
public class CPsolver {

    private ArrayList<Flight> flights;
    private HelperMethods h;
    private int T;
    private int B; // upper bound on the total flights cost
    private String[] args;
    ArrayList<Tuple> tuples; // an array of (airport a, date d) that means that traveller must be at a at time d
    private String solution;


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
        this.solution = "";
    }


    // filters out flights that won't arrive within the specified travel time
    private void filtering() {
        ArrayList<Flight> newflights = new ArrayList<>();
        for(Flight f: this.flights) {
            if (f.date + f.duration <= this.T) newflights.add(f);
        }
        this.flights = newflights;
    }

    private int init(){
        this.model = new Model("TP CPsolver");
        this.S = model.intVarArray("Flights Schedule", flights.size() + 1, 0, flights.size());
        this.z = model.intVar("End of schedule", 2, flights.size());
        this.C = this.model.intVarArray("The cost of each taken flight", flights.size() + 1, 0, 5000000);
        this.cost_sum = this.model.intVar(0, B);
        this.last_flight = this.model.intVar(1, flights.size());
        this.trip_duration = this.model.intVar(1, T);

        this.solver = model.getSolver();

        this.model.sum(C, "=", cost_sum).post();
        if (this.findSchedule() == 0) {
            return 0;
        }
        return 1;
    }

    private int findSchedule() {
        Airport a0 = h.getHomePoint();
        int[] to_home = h.arrayToint(h.allToHome(a0, this.T));
        int[] from_home = h.arrayToint(h.allFrom(a0));

        // trip property 1
        model.member(S[0], from_home).post();
        this.model.arithm(S[1], "!=", 0).post();
        this.model.arithm(C[0], "!=", 0).post();

        int trip_property_5 = tripProperty5();
        if (trip_property_5 == 0) {
            return 0;
        }
        if (this.tuples != null) {
            System.out.println("Searching for solutions with hard constraint 2:");
            hardConstraint2();
        }
//
        for(int i = 1; i <= flights.size(); i++) {
            Flight f = h.getFlightByID(i);
            tripProperties2and3and4(f);
            sequenceConstraints(i, to_home, f);
//
        }
        costConstraint();
        this.model.allDifferentExcept0(S).post();
        return 1;
    }

    // enforces trip properties 2, 3 and 4
    private void tripProperties2and3and4(Flight f){
        ArrayList<Integer> af_conn = h.allFromTimedConn(f);
        ArrayList<Integer> af = h.allFromTimed(f);

        int[] all_from_conn = h.arrayToint(af_conn); // for trip property 3
        // todo there is no need to enforce trip property 4, it is already enforced
        // when last flight is constrained to arrive at the home point
        int[] all_from = h.arrayToint(af); // for trip property 4

        for (int j = 1; j < flights.size(); j++) {
            model.ifThen(
                    model.and(
                            model.arithm(S[j-1],"=", f.id),
                            model.arithm(z, ">", j+1)),
                    model.member(S[j], all_from_conn)
            );
            model.ifThen(
                    model.and(
                            model.arithm(S[j-1],"=", f.id),
                            model.arithm(z, "=", j+1)),
                    model.member(S[j], all_from)
            );
        }
    }

    // all destinations must be visited
    private int tripProperty5(){
        for (Airport d: h.getDestinations()) {
            int[] all_to = h.arrayToint(h.allTo(d)); // all flights that fly to d
            if (all_to.length == 0) {
                System.out.println("It is impossible to visit destination " + d.name + ".\nThe instance has no solution.");
                return 0;
            }
            IntVar X = this.model.intVar(1, all_to.length);
            this.model.among(X, S, all_to).post(); // S must contain at least one flight that goes to d
        }
        return 1;
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

    private void sequenceConstraints(int i, int[] to_home, Flight f){
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

        // if s[i] is not 0, then s[i-1] must not be 0
        model.ifThen(
                model.arithm(S[i], "!=", 0),
                model.arithm(S[i-1], "!=", 0)
        );

        this.model.ifThen(
                model.arithm(S[i], "=", 0),
                model.arithm(C[i], "=", 0)
        );

        this.model.ifThen(
                model.arithm(S[i], "!=", 0),
                model.arithm(C[i], "!=", 0)
        );

        model.ifThen(
                model.arithm(last_flight, "=", i),
                model.arithm(trip_duration, "=", (int) (f.date + f.duration))
        );
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
        System.out.println(a.name + " " + date/10 + " " + index);
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
    /*** end of hard constraint 2 code ***/

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

    public String getSolution() {
        if (init() == 0) {
            return "";
        }

        if (args.length == 2 || (args.length == 3 && args[2].equals("-hc2"))) {
            Solution x = solver.findSolution();
            if (x == null) {
                System.out.println("No solution was found");
            }
            else {
                System.out.println("A solution:");
                printSolution(x, false);
            }
            return getStats();
        }
        Boolean m = null;
        IntVar to_optimise = null;
        Boolean isVerbose = false;
        int isOptimalSearch = 0;

        for (String arg : args) {
//            if (arg.equals("-verbose")) {
//                isVerbose = true;
//            }
            if (arg.equals("-min")) {
                System.out.print("Solution with minimum ");
                m = Model.MINIMIZE;
                isOptimalSearch += 1;
            }
            if (arg.equals("-max")) {
                System.out.print("Solution with maximum ");
                m = Model.MAXIMIZE;
                isOptimalSearch += 1;
            }
            if (arg.equals("-cost")) {
                System.out.println("cost:");
                to_optimise = this.cost_sum;
                isOptimalSearch += 1;
            }
            if (arg.equals("-flights")) {
                System.out.println("number of flights:");
                to_optimise = this.z;
                isOptimalSearch += 1;
            }
            if (arg.equals("-trip_duration")) {
                System.out.println("trip duration:");
                to_optimise = this.trip_duration;
                isOptimalSearch += 1;
            }
            if (arg.equals("-allOpt")) {
                System.out.println("All optimal solutions are:");
                printAllSols(solver.findAllOptimalSolutions(to_optimise, m), isVerbose);
                return getStats();
            }
            if (arg.equals("-all")) {
                System.out.println("All solutions are:");
                printAllSols(solver.findAllSolutions(), isVerbose);
                return getStats();
            }
        }
        if (isOptimalSearch == 2) {
            returnOneOptimal(m, to_optimise, isVerbose);
        } else if (isOptimalSearch == 1) {
            System.out.println("\nNot enough arguments provided");
            return "";
        }
        return getStats();
    }

    private String getStats() {
        System.out.println("nodes: " + solver.getMeasures().getNodeCount() +
                "   cpu: " + solver.getMeasures().getTimeCount());
        return this.solution + "nodes: " + solver.getMeasures().getNodeCount() +
                "   cpu: " + solver.getMeasures().getTimeCount();
    }

    private void returnOneOptimal(Boolean m, IntVar to_optimise, Boolean isVerbose) {
        Solution x = solver.findOptimalSolution(to_optimise, m);
        if (x == null) {
            System.out.println("No optimal solution was found");
            return;
        }
        printSolution(x, isVerbose);
    }

    private void printAllSols(List<Solution> solutions, Boolean isVerbose) {
        for (Solution sol : solutions) {
            if (sol != null) printSolution(sol, isVerbose);
        }
    }

    private void printSolution(Solution x, Boolean isVerbose) {
        System.out.print("  ");
        for (int i = 0; i < x.getIntVal(z); i++) {
            System.out.print(x.getIntVal(S[i]) + " ");
        }
        System.out.println();
        for (int i = 0; i < x.getIntVal(z); i++) {
            String nextVar = x.getIntVal(S[i]) + " ";
            System.out.print("  Flight with id " + nextVar);
            this.solution += nextVar;
            System.out.print("from " + h.getFlightByID(x.getIntVal(S[i])).dep.name);
            System.out.print(" to " + h.getFlightByID(x.getIntVal(S[i])).arr.name);
            System.out.print(" on date: " + h.getFlightByID(x.getIntVal(S[i])).date / 10);
            System.out.println(" costs: " + h.getFlightByID(x.getIntVal(S[i])).cost / 100);
        }
        String tripDuration = "  Trip duration: " + x.getIntVal(trip_duration)/10;
        String totalCost = " Total cost: " + x.getIntVal(cost_sum)/100 + "\n";
        System.out.print(tripDuration);
        System.out.println(totalCost);
        this.solution += tripDuration + totalCost;
    }

}
