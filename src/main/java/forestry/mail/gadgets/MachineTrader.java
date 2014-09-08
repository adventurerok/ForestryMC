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
package forestry.mail.gadgets;

import java.util.LinkedList;

import forestry.api.mail.MailAddress;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.common.util.ForgeDirection;

import buildcraft.api.gates.ITrigger;

import forestry.api.core.ForestryAPI;
import forestry.api.core.ISpecialInventory;
import forestry.api.mail.IStamps;
import forestry.api.mail.PostManager;
import forestry.core.EnumErrorCode;
import forestry.core.gadgets.TileBase;
import forestry.core.network.EntityNetData;
import forestry.core.network.GuiId;
import forestry.core.proxy.Proxies;
import forestry.core.utils.InventoryAdapter;
import forestry.core.utils.StackUtils;
import forestry.mail.TradeStation;
import forestry.plugins.PluginMail;

public class MachineTrader extends TileBase implements ISpecialInventory, ISidedInventory {

	@EntityNetData
	public MailAddress address;

	public MachineTrader() {
		address = new MailAddress();
	}

	@Override
	public String getInventoryName() {
		return "mail.1.name";
	}

	@Override
	public void openGui(EntityPlayer player, TileBase tile) {
		if (isLinked())
			player.openGui(ForestryAPI.instance, GuiId.TraderGUI.ordinal(), worldObj, xCoord, yCoord, zCoord);
		else
			player.openGui(ForestryAPI.instance, GuiId.TraderNameGUI.ordinal(), worldObj, xCoord, yCoord, zCoord);
	}

	@Override
	public void onRemoval() {
		if (isLinked()) {
			PostManager.postRegistry.deleteTradeStation(worldObj, address);
		}
	}

	/* SAVING & LOADING */
	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);

		if (address != null) {
			NBTTagCompound nbt = new NBTTagCompound();
			address.writeToNBT(nbt);
			nbttagcompound.setTag("address", nbt);
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);

		if (nbttagcompound.hasKey("address")) {
			address = MailAddress.loadFromNBT(nbttagcompound.getCompoundTag("address"));
		}
	}

	/* UPDATING */
	@Override
	public void updateServerSide() {

		if (worldObj.getTotalWorldTime() % 40 * 10 != 0)
			return;

		if (!hasPaperMin(0.0f) || !hasInputBufMin(0.0f)) {
			setErrorState(EnumErrorCode.NORESOURCE);
			return;
		}
		if (!hasPostageMin(3)) { // Assumes that the trade station owner should have a notice.
			setErrorState(EnumErrorCode.NOSTAMPS);
			return;
		}

		setErrorState(EnumErrorCode.OK);
	}

	/* STATE INFORMATION */

	public boolean isLinked() {
		return address.isValid();
	}

	private float percentOccupied(int startSlot, int countSlots, ItemStack item) {
		int max = countSlots * 64;
		int avail = 0;

		IInventory tradeInventory = this.getOrCreateTradeInventory();
		for (int i = startSlot; i < startSlot + countSlots; i++) {
			ItemStack itemInSlot = tradeInventory.getStackInSlot(i);
			if (itemInSlot == null || (item != null && !StackUtils.isIdenticalItem(itemInSlot, item)))
				continue;
			avail += itemInSlot.stackSize;
		}

		return ((float) avail / (float) max);
	}

	public boolean hasPaperMin(float percentage) {
		return percentOccupied(TradeStation.SLOT_LETTERS_1, TradeStation.SLOT_LETTERS_COUNT, new ItemStack(Items.paper)) > percentage;
	}

	public boolean hasInputBufMin(float percentage) {
		IInventory inventory = getOrCreateTradeInventory();
		ItemStack tradegood = inventory.getStackInSlot(TradeStation.SLOT_TRADEGOOD);
		if (tradegood == null)
			return true;
		return percentOccupied(TradeStation.SLOT_BUFFER, TradeStation.SLOT_BUFFER_COUNT, tradegood) > percentage;
	}

	public boolean hasOutputBufMin(float percentage) {
		return percentOccupied(TradeStation.SLOT_BUFFER, TradeStation.SLOT_BUFFER_COUNT, null) > percentage;
	}

	public boolean hasPostageMin(int postage) {

		int posted = 0;

		IInventory tradeInventory = this.getOrCreateTradeInventory();
		for (int i = TradeStation.SLOT_STAMPS_1; i < TradeStation.SLOT_STAMPS_1 + TradeStation.SLOT_STAMPS_COUNT; i++) {
			ItemStack stamp = tradeInventory.getStackInSlot(i);
			if (stamp == null)
				continue;
			if (!(stamp.getItem() instanceof IStamps))
				continue;

			posted += ((IStamps) stamp.getItem()).getPostage(stamp).getValue() * stamp.stackSize;
		}

		return posted >= postage;
	}

	/* ADDRESS */
	public MailAddress getAddress() {
		return address;
	}

	public void setAddress(MailAddress address) {
		if (address == null)
			throw new NullPointerException("address must not be null");

		if (this.address.isValid() && this.address.equals(address))
			return;

		if (Proxies.common.isSimulating(worldObj)) {
			if (!PostManager.postRegistry.isValidTradeAddress(worldObj, address)) {
				setErrorState(EnumErrorCode.NOTALPHANUMERIC);
				return;
			}

			if (!PostManager.postRegistry.isAvailableTradeAddress(worldObj, address)) {
				setErrorState(EnumErrorCode.NOTUNIQUE);
				return;
			}

			this.address = address;
			PostManager.postRegistry.getOrCreateTradeStation(worldObj, getOwnerProfile(), address);
			setErrorState(EnumErrorCode.OK);
			sendNetworkUpdate();
		} else
			this.address = address;
	}

	/* TRADING */
	public IInventory getOrCreateTradeInventory() {

		// Handle client side
		if (!Proxies.common.isSimulating(worldObj))
			return new InventoryAdapter(TradeStation.SLOT_SIZE, "INV");

		if (!address.isValid())
			return new InventoryAdapter(TradeStation.SLOT_SIZE, "INV");

		return PostManager.postRegistry.getOrCreateTradeStation(worldObj, getOwnerProfile(), address);
	}

	/* ISPECIALINVENTORY */
	@Override
	public int addItem(ItemStack stack, boolean doAdd, ForgeDirection from) {

		if (!this.isLinked())
			return 0;

		IInventory inventory = getOrCreateTradeInventory();
		ItemStack tradegood = inventory.getStackInSlot(TradeStation.SLOT_TRADEGOOD);

		// Special handling for paper
		if (stack.getItem() == Items.paper)
			// Handle paper as resource if its not the trade good or pumped in from above or below
			if ((tradegood != null && tradegood.getItem() != Items.paper) || from == ForgeDirection.DOWN || from == ForgeDirection.UP)
				return StackUtils.addToInventory(stack, inventory, doAdd, TradeStation.SLOT_LETTERS_1, TradeStation.SLOT_LETTERS_COUNT);

		// Special handling for stamps
		if (stack.getItem() instanceof IStamps)
			// Handle stamps as resource if its not the trade good or pumped in from above or below
			if ((tradegood != null && !(tradegood.getItem() instanceof IStamps)) || from == ForgeDirection.DOWN || from == ForgeDirection.UP)
				return StackUtils.addToInventory(stack, inventory, doAdd, TradeStation.SLOT_STAMPS_1, TradeStation.SLOT_STAMPS_COUNT);

		// Everything else
		if (tradegood == null)
			return 0;

		if (!tradegood.isItemEqual(stack))
			return 0;

		return StackUtils.addToInventory(stack, inventory, doAdd, TradeStation.SLOT_BUFFER, TradeStation.SLOT_BUFFER_COUNT);
	}

	@Override
	public ItemStack[] extractItem(boolean doRemove, ForgeDirection from, int maxItemCount) {

		if (!this.isLinked())
			return new ItemStack[0];

		ItemStack product = null;
		IInventory inventory = getOrCreateTradeInventory();
		for (int i = TradeStation.SLOT_BUFFER; i < TradeStation.SLOT_BUFFER + TradeStation.SLOT_BUFFER_COUNT; i++) {
			ItemStack stackSlot = inventory.getStackInSlot(i);
			if (stackSlot == null)
				continue;
			if (stackSlot.stackSize <= 0)
				continue;

			product = inventory.decrStackSize(i, 1);
			break;
		}

		if (product != null)
			return new ItemStack[] { product };
		else
			return new ItemStack[0];
	}

	/* ISIDEDINVENTORY */
	private static int[] slotIndices;

	public int[] getSizeInventorySide(int side) {
		IInventory inventory = getOrCreateTradeInventory();
		if(slotIndices == null) {
			slotIndices = new int[inventory.getSizeInventory()];
			for(int i = 0; i < inventory.getSizeInventory(); i++)
				slotIndices[i] = i;
		}
		return slotIndices;
	}

	@Override
	protected boolean canTakeStackFromSide(int slotIndex, ItemStack itemstack, int side) {

		if(!super.canTakeStackFromSide(slotIndex, itemstack, side))
			return false;

		if(slotIndex >= TradeStation.SLOT_BUFFER && slotIndex < TradeStation.SLOT_BUFFER + TradeStation.SLOT_BUFFER_COUNT)
			return true;

		return false;
	}

	@Override
	protected boolean canPutStackFromSide(int slotIndex, ItemStack itemstack, int side) {
		if(!super.canPutStackFromSide(slotIndex, itemstack, side))
			return false;

		if (slotIndex >= TradeStation.SLOT_LETTERS_1 && slotIndex < TradeStation.SLOT_LETTERS_1 + TradeStation.SLOT_LETTERS_COUNT
				&& itemstack.getItem() == Items.paper) {
			return true;
		}

		if (slotIndex >= TradeStation.SLOT_STAMPS_1 && slotIndex < TradeStation.SLOT_STAMPS_COUNT) {
			return itemstack.getItem() instanceof IStamps;
		}

		if (slotIndex >= TradeStation.SLOT_BUFFER && slotIndex < TradeStation.SLOT_BUFFER_COUNT) {
			IInventory inventory = getOrCreateTradeInventory();
			ItemStack tradegood = inventory.getStackInSlot(TradeStation.SLOT_TRADEGOOD);
			if(tradegood == null)
				return false;
			return StackUtils.isIdenticalItem(tradegood, itemstack);
		}

		return false;
	}

	/* IINVENTORY */
	@Override
	public int getSizeInventory() {
		return getOrCreateTradeInventory().getSizeInventory();
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		return getOrCreateTradeInventory().getStackInSlot(i);
	}

	@Override
	public ItemStack decrStackSize(int i, int j) {
		return getOrCreateTradeInventory().decrStackSize(i, j);
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemstack) {
		getOrCreateTradeInventory().setInventorySlotContents(i, itemstack);
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot) {
		return getOrCreateTradeInventory().getStackInSlotOnClosing(slot);
	}

	@Override
	public void markDirty() {
		getOrCreateTradeInventory().markDirty();
	}

	@Override
	public int getInventoryStackLimit() {
		return getOrCreateTradeInventory().getInventoryStackLimit();
	}

	@Override
	public void openInventory() {
	}

	@Override
	public void closeInventory() {
	}

	/**
	 * TODO: just a specialsource workaround
	 */
	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		return super.isUseableByPlayer(player);
	}

	/**
	 * TODO: just a specialsource workaround
	 */
	@Override
	public boolean hasCustomInventoryName() {
		return super.hasCustomInventoryName();
	}

	/**
	 * TODO: just a specialsource workaround
	 */
	@Override
	public boolean isItemValidForSlot(int slotIndex, ItemStack itemstack) {
		return super.isItemValidForSlot(slotIndex, itemstack);
	}

	/**
	 * TODO: just a specialsource workaround
	 */
	@Override
	public boolean canInsertItem(int i, ItemStack itemstack, int j) {
		return super.canInsertItem(i, itemstack, j);
	}

	/**
	 * TODO: just a specialsource workaround
	 */
	@Override
	public boolean canExtractItem(int i, ItemStack itemstack, int j) {
		return super.canExtractItem(i, itemstack, j);
	}

	/**
	 * TODO: just a specialsource workaround
	 */
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return super.getAccessibleSlotsFromSide(side);
	}

	/* ITRIGGERPROVIDER */
	@Override
	public LinkedList<ITrigger> getCustomTriggers() {
		LinkedList<ITrigger> res = new LinkedList<ITrigger>();
		res.add(PluginMail.lowPaper25);
		res.add(PluginMail.lowPaper10);
		res.add(PluginMail.lowInput25);
		res.add(PluginMail.lowInput10);
		res.add(PluginMail.lowPostage40);
		res.add(PluginMail.lowPostage20);
		res.add(PluginMail.highBuffer90);
		res.add(PluginMail.highBuffer75);
		return res;
	}

}
