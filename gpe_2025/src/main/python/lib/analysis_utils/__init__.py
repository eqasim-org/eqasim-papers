import os
import pandas as pd


def read_simulation_csv(sim_path: str, file_name: str, **kwargs):
    df = pd.read_csv(os.path.join(sim_path, file_name), sep=";")
    if len(kwargs) > 0:
        for key, value in kwargs.items():
            df[key] = value
    return df