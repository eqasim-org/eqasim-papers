import os


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
        self.demand_impacting_params = {ServiceParameter(key, value) for key, value in config_dict["demand_impacting"].items()}
        self.non_demand_impacting_params = {ServiceParameter(key, value) for key, value in config_dict["non_demand_impacting"].items()}

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
    def __init__(self, config_dict):
        self.demand_identification_fleet_size = config_dict["demand_identification_fleet_size"]
        self.fleet_sizes = config_dict["fleet_sizes"]
        self.max_rejection_rate = config_dict["max_rejection_rate"]

        assert isinstance(self.demand_identification_fleet_size, int)
        for v in self.fleet_sizes:
            assert isinstance(v, int)
        float(self.max_rejection_rate)

class DeploymentScenario:
    def __init__(self, name, config_dict, services, modified_transit_schedules):
        self.name = name
        self.services = {s: services[s] for s in config_dict["services"]}
        assert len(self.services) <= 2
        assert len(set(s.type for s in self.services.values())) == len(self.services)
        self.simulation_overrides = dict()
        if "simulation_overrides" in config_dict:
            for key, value in config_dict["simulation_overrides"].items():
                if key == "transit_schedule":
                    self.simulation_overrides[key] = modified_transit_schedules[value]
                else:
                    raise Exception("Unsupported simulation override: %s" % key)

class GeneralInputsConfig:
    def __init__(self, config_dict, basedir):
        self.input_path = to_absolute(config_dict["input_path"], basedir)
        self.input_prefix = config_dict["input_prefix"]
        self.area_path = to_absolute(config_dict["area_path"], basedir)
        self.area_prefix = config_dict["area_prefix"]
        assert self.area_prefix != "global_"

class PipelineConfig:
    def __init__(self, config_dict, basedir, max_cores):
        self.output_path = to_absolute(config_dict["output_path"], basedir)
        self.temp_path = to_absolute(config_dict["temp_path"], basedir)
        self.random_seed = int(config_dict["random_seed"])

        self.java = JavaConfig(config_dict["java"])
        self.global_simulation_resources = ResourcesConfig(config_dict["resources"]["global_simulations"], max_cores)
        self.area_simulation_resources = ResourcesConfig(config_dict["resources"]["area_simulations"], max_cores)
        self.general_inputs_config = GeneralInputsConfig(config_dict["general_inputs"], basedir)
        self.modified_transit_schedules = {key: ModifiedTransitScheduleConfig(key, value) for key, value in config_dict["modified_transit_schedules"].items()}
        self.service_parameters_config = ServiceParametersConfig(config_dict["service_parameters"])
        self.services_config = {key: SingleServiceConfig(key, value) for key, value in config_dict["services"].items()}
        self.fleet_sizing_config = FleetSizingConfig(config_dict["fleet_sizing"])
        self.deployment_scenarios = {key: DeploymentScenario(key, value, self.services_config, self.modified_transit_schedules) for key, value in config_dict["deployment_scenarios"].items()}


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

    def get_modified_transit_schedule_path(self, modified_transit_schedule):
        if not isinstance(modified_transit_schedule, ModifiedTransitScheduleConfig):
            modified_transit_schedule = self.modified_transit_schedules[modified_transit_schedule]
        return os.path.join(self.output_path, "modified_transit_schedules", "%s.xml.gz" % modified_transit_schedule.name)

    def get_deployment_scenario_configure_args(self, deployment_scenario):
        if not isinstance(deployment_scenario, DeploymentScenario):
            deployment_scenario = self.deployment_scenarios[deployment_scenario]
        result = ""
        for service in deployment_scenario.services.values():
            if service.type == ServiceType.UNIMODAL:
                result += "--unimodal-availability %s" % service.availability
            else:
                result += "--intermodal-availability %s" % service.availability
                transfer_locations_config = service.transfer_locations
                if len(transfer_locations_config.transit_modes) > 0:
                    result += "--intermodal-transfer-location-modes %s" % ",".join(transfer_locations_config.transit_modes)
                if len(transfer_locations_config.transit_stops) > 0:
                    result += "--intermodal-transfer-location-ids %s" % ",".join(transfer_locations_config.transit_stops)
        for key, value in deployment_scenario.simulation_overrides.items():
            if key == "transit_schedule":
                result += "--config:transit:transitScheduleFile %s" % self.get_modified_transit_schedule_path(value)

class SimulationConfig:
    def __init__(self, deployment_scenario: DeploymentScenario, service_parameters_config: ServiceParametersConfig, service_parameters_values: dict):
        self.deployment_scenario = deployment_scenario
        self.services_parameters_config = service_parameters_config
        self.services_parameters_values = service_parameters_values