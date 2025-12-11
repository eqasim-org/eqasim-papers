import json
import os
import hashlib
import itertools
import random

def to_absolute(in_path, ref_path):
    if in_path is None:
        return None
    if os.path.isabs(in_path):
        return in_path
    return os.path.normpath(os.path.join(ref_path,in_path))

def memory_string_to_mb(memory_string):
    assert isinstance(memory_string, str)
    nb = int(memory_string[0:len(memory_string)-1])
    if memory_string.endswith("G"):
        nb *= 1024
    elif memory_string.endswith("M"):
        raise Exception("Bad memory format")
    return nb

class JavaConfig:
    def __init__(self, config_dict):
        self.java_cmd = config_dict["java_cmd"]
        self.mvn_cmd = config_dict["mvn_cmd"]
        self.mvn_local_repo = config_dict["local_maven_repo"]

class ResourcesConfig:
    def __init__(self, config_dict, max_cores):
        self.threads = min(max_cores, int(config_dict["threads"]))
        self.memory = config_dict["memory"]
        self.runtime = 60 * 24
        if "runtime" in config_dict:
            self.runtime = int(config_dict["runtime"])

class ModifiedTransitScheduleConfig:
    def __init__(self, name, config_dict):
        self.name = name
        self.criteria = config_dict["criteria"]
        self.transit_modes = config_dict["transit_modes"]
        self.scope = config_dict["scope"]
        self.threshold = config_dict["threshold"]

class ServiceParameter:
    def __init__(self, name, values):
        self.name = name
        if isinstance(values, list):
            self.values = values
            self.separate_per_service = False
        elif isinstance(values, dict):
            self.values = values["values"]
            self.separate_per_service = values["separate_per_service"]
            assert isinstance(self.values, list)
        else:
            raise Exception("Wrong service parameter format")
        self.check()

    def check(self):
        if len(self.values) != len(set(self.values)):
            raise Exception("Service parameter contains duplicate values")
        if self.name == "operational_scheme":
            assert set(self.values).issubset(["stop_based", "door_to_door"])
        elif self.name == "price":
            [float(p) for p in self.values]
        elif self.name == "detour_factor":
            for v in set(self.values):
                assert isinstance(v, str) and v.startswith("+") and v[-1] in ["%", "s"]
                float(v[1:-1])
        elif self.name in ["vehicle_capacity", "prebooking", "max_wait_time"]:
            [int(v) for v in self.values]
        else:
            raise Exception("Unsupported service parameter: %s" % self.name)

class ServiceParametersConfig:
    def __init__(self, config_dict):
        self.demand_impacting_params = {key: ServiceParameter(key, value) for key, value in config_dict["demand_impacting"].items()}
        self.non_demand_impacting_params = {key: ServiceParameter(key, value) for key, value in config_dict["non_demand_impacting"].items()}

class ServiceType:
    UNIMODAL = "unimodal"
    INTERMODAL = "intermodal"
    def __init__(self):
        raise Exception("This class is not meant to be instantiated")

    @staticmethod
    def check(service_type):
        assert service_type in [ServiceType.UNIMODAL, ServiceType.INTERMODAL]
        return service_type

class ServiceAvailability:
    ALL_DAY = "all_day"
    OFF_PEAK = "off_peak"
    def __init__(self):
        raise Exception("This class is not meant to be instantiated")

    @staticmethod
    def check(availability: str):
        assert availability in [ServiceAvailability.ALL_DAY, ServiceAvailability.OFF_PEAK]
        return availability

class TransferLocationsConfig:

    def __init__(self, config_dict):
        self.transit_modes = []
        self.transit_stops = []
        if "transit_modes" in config_dict:
            self.transit_modes = config_dict["transit_modes"]
        if "transit_stops" in config_dict:
            self.transit_stops = config_dict["transit_stops"]

class SingleServiceConfig:
    def __init__(self, name, config_dict):
        self.name = name
        self.type = ServiceType.check(config_dict["type"])
        self.availability = ServiceAvailability.check(config_dict["availability"])
        self.transfer_locations = None
        if self.type == ServiceType.INTERMODAL:
            self.transfer_locations = TransferLocationsConfig(config_dict["transfer_locations"])
        else:
            assert "transfer_locations" not in config_dict

class FleetSizingConfig:
    def __init__(self, config_dict, random_seed):
        self.demand_identification_fleet_size = config_dict["demand_identification_fleet_size"]
        r = random.Random(random_seed)
        self.fleet_seeds = [r.randint(1, 999999) for _ in range(int(config_dict["fleet_seeds"]))]
        self.max_rejection_rate = config_dict["max_rejection_rate"]
        self.fleet_size_precision = config_dict["fleet_size_precision"]
        assert self.demand_identification_fleet_size % self.fleet_size_precision == 0

        assert isinstance(self.demand_identification_fleet_size, int)
        float(self.max_rejection_rate)

class DeploymentScenario:
    def __init__(self, name, config_dict, services, modified_transit_schedules):
        self.name = name
        self.services = {s: services[s] for s in config_dict["services"]}
        assert len(self.services) <= 2
        assert len(self.service_types) == len(self.services)
        self.simulation_overrides = dict()
        if "simulation_overrides" in config_dict:
            for key, value in config_dict["simulation_overrides"].items():
                if key == "transit_schedule":
                    self.simulation_overrides[key] = modified_transit_schedules[value]
                else:
                    raise Exception("Unsupported simulation override: %s" % key)

    @property
    def transit_schedule_override(self):
        return self.simulation_overrides["transit_schedule"] if "transit_schedule" in self.simulation_overrides else None

    @property
    def service_types(self):
        return set(s.type for s in self.services.values())

class GeneralInputsConfig:
    def __init__(self, config_dict, basedir):
        self.input_path = to_absolute(config_dict["input_path"], basedir)
        self.input_prefix = config_dict["input_prefix"]
        self.area_path = to_absolute(config_dict["area_path"], basedir)
        self.area_prefix = config_dict["area_prefix"]
        self.area_buffer_length = int(config_dict["area_buffer_length"])
        self.sampling = config_dict["sampling"]
        assert self.area_prefix != "global_"

def dctproduct(dct):
    """
    >>> list(dctproduct({'number': [1, 2], 'character': 'ab'}))
    [{'number': 1, 'character': 'a'}, {'number': 1, 'character': 'b'}, {'number': 2, 'character': 'a'}, {'number': 2, 'character': 'b'}]
    """
    keys = dct.keys()
    for vals in itertools.product(*dct.values()):
        yield dict(zip(keys, vals))

class PipelineConfig:

    RELEVANT_SIMULATION_OUTPUTS = ["eqasim_trips.csv", "eqasim_legs.csv", "eqasim_pt.csv", "output_events.xml.gz",
                                   "output_plans.xml.gz"]

    def __init__(self, config_dict, basedir, max_cores):
        self.output_path = to_absolute(config_dict["output_path"], basedir)
        self.temp_path = to_absolute(config_dict["temp_path"], basedir)
        self.random_seed = int(config_dict["random_seed"])

        self.java = JavaConfig(config_dict["java"])
        self.global_simulation_resources = ResourcesConfig(config_dict["resources"]["global_simulations"], max_cores)
        self.area_simulation_resources = ResourcesConfig(config_dict["resources"]["area_simulations"], max_cores)
        self.cutter_resources = ResourcesConfig(config_dict["resources"]["cutter"], max_cores)
        self.area_routing_resources = ResourcesConfig(config_dict["resources"]["area_routing"], max_cores)
        self.general_inputs_config = GeneralInputsConfig(config_dict["general_inputs"], basedir)
        self.modified_transit_schedules = {key: ModifiedTransitScheduleConfig(key, value) for key, value in config_dict["modified_transit_schedules"].items()}
        self.service_parameters_config = ServiceParametersConfig(config_dict["service_parameters"])
        self.services_config = {key: SingleServiceConfig(key, value) for key, value in config_dict["services"].items()}
        self.fleet_sizing_config = FleetSizingConfig(config_dict["fleet_sizing"], self.random_seed)
        self.deployment_scenarios = {key: DeploymentScenario(key, value, self.services_config, self.modified_transit_schedules) for key, value in config_dict["deployment_scenarios"].items()}

        self.simulation_configs = dict()
        for deployment_scenario in self.deployment_scenarios.values():
            new_configs = self.get_deployment_scenario_simulation_configs(deployment_scenario)
            assert len(set(self.simulation_configs.keys()).intersection(new_configs.keys())) == 0
            self.simulation_configs.update(new_configs)


    @staticmethod
    def get_relevant_simulation_inputs(base_path):
        return [os.path.join(base_path, f) for f in PipelineConfig.RELEVANT_SIMULATION_OUTPUTS]

    @property
    def global_simulation_inputs_location(self):
        return os.path.join(self.temp_path, "simulation_inputs")

    def global_simulation_input_file_path(self, file_name):
        return os.path.join(self.global_simulation_inputs_location, "%s%s" % (self.general_inputs_config.input_prefix, file_name))

    @property
    def global_baseline_config_path(self):
        return self.global_simulation_input_file_path("config_baseline.xml")

    @property
    def global_simulation_outputs_location(self):
        return os.path.join(self.output_path, "simulations", "global_baseline")

    def global_simulation_output_file_path(self, file_name):
        return os.path.join(self.global_simulation_outputs_location, file_name)

    @property
    def area_simulation_inputs_location(self):
        return os.path.join(self.output_path, "scenarios", "%sscenario" % self.general_inputs_config.area_prefix)

    def area_simulation_input_file_path(self, file_name):
        return os.path.join(self.area_simulation_inputs_location, "%s%s" % (self.general_inputs_config.area_prefix, file_name))

    @property
    def area_baseline_config_path(self):
        return self.area_simulation_input_file_path("config.xml")

    @property
    def area_baseline_simulation_outputs_location(self):
        return os.path.join(self.output_path, "simulations", "%sbaseline" % self.general_inputs_config.area_prefix)

    def area_baseline_simulation_output_file_path(self, file_name):
        return os.path.join(self.area_baseline_simulation_outputs_location, file_name)

    @property
    def area_vehicles_files_location(self):
        return self.area_simulation_input_file_path("drt_vehicles")

    def area_vehicles_file_path(self, fleet_size: int, vehicle_capacity: int):
        file_name = "%d_%d.xml" % (fleet_size, vehicle_capacity)
        return os.path.join(self.area_vehicles_files_location, file_name)

    def get_modified_transit_schedule_location(self, modified_transit_schedule):
        if not isinstance(modified_transit_schedule, ModifiedTransitScheduleConfig):
            modified_transit_schedule = self.modified_transit_schedules[modified_transit_schedule]
        return os.path.join(self.output_path, "modified_transit_schedules", modified_transit_schedule.name)

    def get_modified_transit_schedule_path(self, modified_transit_schedule):
        return os.path.join(str(self.get_modified_transit_schedule_location(modified_transit_schedule)), "transit_schedule.xml.gz")

    def get_modified_transit_schedule_params(self, modified_transit_schedule):
        if not isinstance(modified_transit_schedule, ModifiedTransitScheduleConfig):
            modified_transit_schedule = self.modified_transit_schedules[modified_transit_schedule]
        params = dict()
        params["criteria"] = modified_transit_schedule.criteria
        params["scope"] = modified_transit_schedule.scope
        params["threshold"] = modified_transit_schedule.threshold
        params["transit_modes"] = modified_transit_schedule.transit_modes
        params["sampling"] = self.general_inputs_config.sampling
        return params

    def get_deployment_scenario_config_path(self, deployment_scenario):
        if not isinstance(deployment_scenario, DeploymentScenario):
            deployment_scenario = self.deployment_scenarios[deployment_scenario]
        return self.area_simulation_input_file_path("config_%s.xml" % deployment_scenario.name)

    def get_cost_parameters_file_path(self, deployment_scenario, unitary_cost):
        assert isinstance(unitary_cost, str)
        if not isinstance(deployment_scenario, DeploymentScenario):
            deployment_scenario = self.deployment_scenarios[deployment_scenario]
        return os.path.join(self.area_simulation_inputs_location, "cost_parameters", deployment_scenario.name, "%s.yaml" % unitary_cost)

    def get_cost_params(self, deployment_scenario, unitary_cost):
        assert isinstance(unitary_cost, str)
        if not isinstance(deployment_scenario, DeploymentScenario):
            deployment_scenario = self.deployment_scenarios[deployment_scenario]

        params = {"drtCost_EUR_access": 0,
                  "drtCost_EUR_km": 0,
                  "feederDrtCost_EUR_access": 0,
                  "feederDrtCost_EUR_km": 0}

        unitary_cost = float(unitary_cost)
        if "unimodal" in deployment_scenario.service_types:
            params["drtCost_EUR_access"] = float(unitary_cost)
            params["drtCost_EUR_km"] = float(unitary_cost)
        if "intermodal" in deployment_scenario.service_types:
            params["feederDrtCost_EUR_km"] = float(unitary_cost)
        return params

    def get_deployment_scenario_configure_inputs(self, deployment_scenario):
        if not isinstance(deployment_scenario, DeploymentScenario):
            deployment_scenario = self.deployment_scenarios[deployment_scenario]
        inputs = [self.area_baseline_config_path, self.area_simulation_input_file_path("drt_stops.xml"), self.general_inputs_config.area_path]
        transit_schedule_override = deployment_scenario.transit_schedule_override
        if transit_schedule_override is not None:
            inputs.append(self.get_modified_transit_schedule_path(transit_schedule_override))
            inputs.append(os.path.join(str(self.get_modified_transit_schedule_location(transit_schedule_override)), "plans.xml.gz"))
        return inputs

    def get_deployment_scenario_configure_args(self, deployment_scenario):
        if not isinstance(deployment_scenario, DeploymentScenario):
            deployment_scenario = self.deployment_scenarios[deployment_scenario]
        result = []
        for service in deployment_scenario.services.values():
            if service.type == ServiceType.UNIMODAL:
                result.append("--unimodal-availability %s" % service.availability)
            else:
                result.append("--intermodal-availability %s" % service.availability)
                transfer_locations_config = service.transfer_locations
                if len(transfer_locations_config.transit_modes) > 0:
                    result.append("--intermodal-transfer-location-modes %s" % ",".join(transfer_locations_config.transit_modes))
                if len(transfer_locations_config.transit_stops) > 0:
                    result.append("--intermodal-transfer-location-ids %s" % ",".join(transfer_locations_config.transit_stops))
        for key, value in deployment_scenario.simulation_overrides.items():
            if key == "transit_schedule":
                result.append("--config:transit.transitScheduleFile %s" % self.get_modified_transit_schedule_path(value))
                result.append("--config:plans.inputPlansFile %s" % os.path.join(str(self.get_modified_transit_schedule_location(value)), "plans.xml.gz"))
        if "intermodal" in deployment_scenario.service_types:
            result.append("--config:eqasim.estimator[mode=feeder_drt].estimator DefaultFeederDrtUtilityEstimator")
        result.append("--config:controller.outputDirectory %s" % deployment_scenario.name)
        return " ".join(result)

    def get_deployment_scenario_simulation_configs(self, deployment_scenario):
        if not isinstance(deployment_scenario, DeploymentScenario):
            deployment_scenario = self.deployment_scenarios[deployment_scenario]

        parameters_dict = dict()
        for param_name, param in list(self.service_parameters_config.demand_impacting_params.items()) + list(self.service_parameters_config.non_demand_impacting_params.items()):
            values = param.values
            if param.separate_per_service:
                values = list(dctproduct({service_type: values for service_type in deployment_scenario.service_types}))
            assert isinstance(values, list)
            parameters_dict[param_name] = values
        simulation_configs = dict()
        for demand_impacting_parameters_values in dctproduct({key: parameters_dict[key] for key in self.service_parameters_config.demand_impacting_params.keys()}):
            demand_identification_parameter_values = dict(**demand_impacting_parameters_values)
            for key in self.service_parameters_config.non_demand_impacting_params.keys():
                demand_identification_parameter_values[key] = parameters_dict[key][0]
            demand_identification_simulation_config = SimulationConfig(deployment_scenario, self.service_parameters_config, demand_identification_parameter_values)
            for non_demand_impacting_parameters_values in dctproduct(({key: parameters_dict[key] for key in self.service_parameters_config.non_demand_impacting_params.keys()})):
                parameter_values = dict(**demand_impacting_parameters_values)
                parameter_values.update(non_demand_impacting_parameters_values)
                simulation_config = SimulationConfig(deployment_scenario, self.service_parameters_config,
                                                     parameter_values)
                assert simulation_config.hash_code not in simulation_configs
                simulation_config.demand_source = demand_identification_simulation_config.hash_code
                simulation_configs[simulation_config.hash_code] = simulation_config

            assert demand_identification_simulation_config.hash_code in simulation_configs
            assert simulation_configs[demand_identification_simulation_config.hash_code].hash_code == simulation_configs[demand_identification_simulation_config.hash_code].demand_source
        return simulation_configs

    def hash_to_config(self, hash_code):
        if hash_code not in self.simulation_configs:
            raise Exception("Simulation config with hash '%s' not found among the %d configs" % (hash_code, len(self.simulation_configs)))
        return self.simulation_configs[hash_code]

    def get_simulation_inputs(self, hash_code, demand_identification, fleet_size=None, random_seed=None, **kwargs):
        simulation_config = self.hash_to_config(hash_code)
        inputs = dict(config=self.get_deployment_scenario_config_path(simulation_config.deployment_scenario))

        if demand_identification:
            assert fleet_size is None and random_seed is None
            fleet_size = self.fleet_sizing_config.demand_identification_fleet_size
            random_seed = self.fleet_sizing_config.fleet_seeds[0]
        else:
            assert fleet_size is not None and random_seed is not None

        if isinstance(fleet_size, str):
            fleet_size = int(fleet_size)
        if isinstance(random_seed, str):
            random_seed = int(random_seed)

        inputs["vehicles"] = "%s/%d_%d_%d.xml" % (self.area_vehicles_files_location, fleet_size, simulation_config.services_parameters_values["vehicle_capacity"], random_seed)

        inputs["cost_params"] = self.get_cost_parameters_file_path(simulation_config.deployment_scenario, simulation_config.services_parameters_values["price"])

        transit_schedule = simulation_config.deployment_scenario.transit_schedule_override

        if transit_schedule is not None:
            inputs["transit_schedule"] = self.get_modified_transit_schedule_path(transit_schedule)
        else:
            inputs["transit_schedule"] = self.area_simulation_input_file_path("transit_schedule.xml.gz")

        if not demand_identification:
            inputs["plans"] = "%s/simulations/demand_identification/%s/%s/output_plans.xml.gz" % (self.output_path, simulation_config.deployment_scenario.name, simulation_config.demand_source)
            inputs["dvrp_travel_times"] = "%s/simulations/demand_identification/%s/%s/dvrp_travel_times.csv.gz" % (self.output_path, simulation_config.deployment_scenario.name, simulation_config.demand_source)
        else:
            if transit_schedule is not None:
                inputs["plans"] = "%s/plans.xml.gz" % self.get_modified_transit_schedule_location(transit_schedule)
            else:
                inputs["plans"] = self.area_baseline_simulation_output_file_path("output_plans.xml.gz")

        inputs.update(kwargs)
        return inputs

    def get_simulation_args(self, hash_code, demand_identification):
        simulation_config = self.hash_to_config(hash_code)
        args = list()
        detour_factor = simulation_config.services_parameters_values["detour_factor"]
        detour_factor_value = float(detour_factor[1:-1])
        if detour_factor.endswith("%"):
            detour_factor_value += 100
            detour_factor_value /= 100
            args.append("--config:multiModeDrt.drt[mode=drt].drtOptimizationConstraints[*=*].drtOptimizationConstraintsSet[*=*].maxTravelTimeAlpha %f" % detour_factor_value)
        else:
            args.append("--config:multiModeDrt.drt[mode=drt].drtOptimizationConstraints[*=*].drtOptimizationConstraintsSet[*=*].maxAbsoluteDetour %f" % detour_factor_value)

        operational_scheme = simulation_config.services_parameters_values["operational_scheme"]
        if operational_scheme == "stop_based":
            args.append("--config:multiModeDrt.drt[mode=drt].operationalScheme stopbased")

        prebooking = simulation_config.services_parameters_values["prebooking"]
        assert isinstance(prebooking, dict)
        if "unimodal" in prebooking and prebooking["unimodal"] > 0:
            args.append("--unimodal-prebooking %d" % prebooking["unimodal"])
        if "intermodal" in prebooking and prebooking["intermodal"] > 0:
            args.append("--intermodal-prebooking %d" % prebooking["intermodal"])

        if demand_identification:
            args.append("--config:controller.lastIteration 100")
        else:
            args.append("--config:controller.lastIteration 0")
        return " ".join(args)

class SimulationConfig:
    def __init__(self, deployment_scenario: DeploymentScenario, service_parameters_config: ServiceParametersConfig, service_parameters_values: dict):
        self.deployment_scenario = deployment_scenario
        self.services_parameters_config = service_parameters_config
        self.services_parameters_values = service_parameters_values
        self.hash_code = SimulationConfig.hash(self)
        self.demand_source = None

        for key, value in self.services_parameters_values.items():
            if key == "prebooking":
                assert isinstance(value, dict)
                for service_type in value.keys():
                    assert service_type in deployment_scenario.service_types
            else:
                assert not isinstance(value, dict)

    def get_dict(self):
        d = dict(**self.services_parameters_values)
        assert "deployment_scenario" not in d
        assert "fleet_size" not in d
        d["deployment_scenario"] = self.deployment_scenario.name
        return d

    @staticmethod
    def dict_to_deterministic_list(d):
        if isinstance(d, dict):
            keys = list(d.keys())
            keys.sort()
            return [(key, SimulationConfig.dict_to_deterministic_list(d[key])) for key in keys]
        return d

    @staticmethod
    def hash(simulation_config):
        d = simulation_config.get_dict()
        l = SimulationConfig.dict_to_deterministic_list(d)
        return str(hashlib.sha256(str(l).encode("utf-8")).hexdigest())