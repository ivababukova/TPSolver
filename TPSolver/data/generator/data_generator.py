from pprint import pprint
import json
import random
import sys

def chooseHubs():
    hub_numb = 0.05 * len(allairports) # the airport hubs are going to be 5% of all airports
    hub_airports = []
    while len(hub_airports) < hub_numb:
        new_hub = random.randint(0, len(allairports) - 1)
        found = False
        for i in range(0, len(hub_airports) - 1):
            if hub_airports[i] == new_hub:
                found = True
                break
        if not found:
            hub_airports.append(new_hub)
    return hub_airports

def airport_names():
    with open("airports.json") as data_file:
        data = json.load(data_file)
        airs_json = data["airports"]
    airs = []
    for a in airs_json:
        airs.append(a.get('airport'))
    return airs


def get_n_airport_names():
    airs = []
    for i in range(0, n):
        rand_num = random.randint(0, len(pool) - 1)
        airs.append(pool.pop(rand_num))
    return airs

def get_weighted_airport_names():
    choice_list = []
    for a in allairports:
        if a["purpose"] == "destination" or a["purpose"] == "home_point":
            for time in range(1,10):
                choice_list.append(a["name"])
        if a["purpose"] == "connection":
            choice_list.append(a["name"])
    return choice_list
    
    
def get_n_flights():
    
    airps = weighted_airports
    
    flights = []
    for i in range(0, m-1):
        rand_dep_num = random.randint(0, len(airps)-1)
        allowed_arr = list(range(0, rand_dep_num)) + list(range(rand_dep_num+1, len(airps)-1))
        rand_arr_num = random.choice(allowed_arr)
        max_date = T - max_dur*10 # if max date is left to be T, many flights depart too late to be useful

        rand_dep = airps[rand_dep_num]
        rand_arr = airps[rand_arr_num]
        rand_date = random.randint(0, max_date) / 10
        rand_duration = round(random.uniform(min_dur, max_dur), 1)
        rand_cost = random.randint(min_cost, max_cost) / 10

        f = {
            "id": i+1,
            "dep_airport": rand_dep,
            "arr_airport": rand_arr,
            "date": rand_date,
            "duration": rand_duration,
            "price": rand_cost
        }

        flights.append(f)
    return flights


def get_n_airports():
    airports = []
    dest_num = 0
    home_point = random.randint(0, len(airport_names)-1)

    for i in range(0, len(airport_names)):
        purpose = ""
        rand_conn = round(random.uniform(min_conn, max_conn ), 1)
        if i == home_point:
            purpose = "home_point"
        elif dest_num == d:
            purpose = "connection"
        else:
            j = random.randint(0, 1)
            if j == 0:
                purpose = "connection"
            elif j == 1:
                purpose = "destination"
                dest_num += 1

        a = {
            "name": airport_names[i],
            "connection_time": rand_conn,
            "purpose": purpose
        }
        airports.append(a)
    return airports

def construct_json():
    data_name = "../random_data/" + str(m) + "_" + str(n) + "_" + str(d) + "_" + id + ".json"
    with open(data_name, "w") as outfile:
        json.dump(data, outfile, sort_keys=True, indent=4)
    print("Successfully written data to file")


# run: python data_generator.py n m T max_cost
if len(sys.argv) < 12:
    print("usage: python data_generator.py "
          "\n   <number of flights>"
          "\n   <number of airports>"
          "\n   <number of destinations>"
          "\n   <holiday time>"
          "\n   <min_flight_cost>"
          "\n   <max_flight_cost>"
          "\n   <min_duration>"
          "\n   <max_duration>"
          "\n   <min_connection_time>"
          "\n   <max_connection_time>"
          "\n   <number to be appended to the name of the output file>"
          )
    exit(1)

# number of flights:
m = int(sys.argv[1])
# number of airports:
n = int(sys.argv[2])
# max number of destinations:
d = int(sys.argv[3])
# holiday time:
T = int(sys.argv[4])*10
# minimum flight cost:
min_cost = int(sys.argv[5])*10
# maximum cost:
max_cost = int(sys.argv[6])*10
# minimum flight duration:
min_dur = float(sys.argv[7])
# maximum flight duration:
max_dur = float(sys.argv[8])
# minimum connection time
min_conn = float(sys.argv[9])
# maximum connection time
max_conn = float(sys.argv[10])
# output file id
id = sys.argv[11]

# print("number of airports: ", n, "\nnumber of flights: ", m, "\nholiday time: ", T, "\nmax flight cost: ", max_cost,
#       "\n minimum cost: ", min_cost, "\nmaximum duration: ", max_dur, "\nmaximum conn time: ", max_conn)

pool = airport_names()
if n > len(pool):
    print("The number of airports should be less or equal to ", len(pool))
    exit(1)

airport_names = get_n_airport_names()
allairports = get_n_airports()
weighted_airports = get_weighted_airport_names()
allflights = get_n_flights()

data = {
    "airports": allairports,
    "flights": allflights,
    "holiday_time": T/10
    }

construct_json()
