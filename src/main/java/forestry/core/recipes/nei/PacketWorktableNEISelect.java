/*******************************************************************************
 * Copyright (c) 2011-2014 SirSengir.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Various Contributors including, but not limited to:
 * SirSengir (original work), CovertJaguar, Player, Binnie, MysteriousAges
 ******************************************************************************/
package forestry.core.recipes.nei;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import forestry.core.gui.slots.SlotCraftMatrix;
import forestry.core.network.DataInputStreamForestry;
import forestry.core.network.IForestryPacketServer;
import forestry.core.network.PacketIdServer;
import forestry.core.network.PacketNBT;
import forestry.factory.gui.ContainerWorktable;

public class PacketWorktableNEISelect extends PacketNBT implements IForestryPacketServer {
	private static final SetRecipeCommandHandler worktableNEISelectHandler = new SetRecipeCommandHandler(ContainerWorktable.class, SlotCraftMatrix.class);

	public PacketWorktableNEISelect() {
	}

	public PacketWorktableNEISelect(NBTTagCompound nbttagcompound) {
		super(PacketIdServer.WORKTABLE_NEI_SELECT, nbttagcompound);
	}

	@Override
	public void onPacketData(DataInputStreamForestry data, EntityPlayerMP player) throws IOException {
		worktableNEISelectHandler.handle(getTagCompound(), player);
	}
}