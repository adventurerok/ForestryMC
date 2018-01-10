package forestry.climatology.tiles;

import forestry.climatology.api.climate.source.IClimateSourceProxy;
import forestry.climatology.climate.ClimateSource;

public interface IClimatiserDefinition<P extends IClimateSourceProxy> {
	ClimateSource createSource(P proxy);
}
