package com.mcal.MCPEDumper.util;
import com.mcal.MCPEDumper.nativeapi.*;
import java.util.*;
import com.mcal.MCPEDumper.*;
import com.mcal.MCPEDumper.vtable.*;

public class HeaderGenerator
{
	private MCPEClass mcpeClass;
	private MCPEVtable vtable;
	private String[] namespace;
	private String className;
	private String path;

	public HeaderGenerator(MCPEClass mcpeClass, MCPEVtable vtable, String path)
	{
		this.mcpeClass = mcpeClass;
		this.vtable = vtable;
		this.path = path;

		this.className = mcpeClass.getName();
		if (this.className.lastIndexOf("::") != -1)
		{
			this.className = this.className.substring(this.className.lastIndexOf("::") + 2, this.className.length());
		}
		try
		{
			String namespaces = mcpeClass.getName().substring(0, mcpeClass.getName().length() - className.length() - 2);
			if (namespaces == null)
				this.namespace = new String[0];
			else
				this.namespace = namespaces.split("::");
		}
		catch (Exception e)
		{
			this.namespace = new String[0];
			e.printStackTrace();
		}
	}

	private String[] getExtendedClasses()
	{
		try
		{
			Vector<String> otherZTVs=new Vector<String>();
			try
			{
				for (MCPESymbol sym:vtable.getVtables())
					if (isMethodBelongToOtherClass(sym))
					{
						boolean hasItem=false;
						for (String str:otherZTVs)
							if (str.equals(getOwnerClass(sym)))
								hasItem = true;
						if (!hasItem)
							otherZTVs.addElement(getOwnerClass(sym));
					}
			}
			catch (Exception e)
			{
				return null;
			}
			if (otherZTVs.isEmpty())
				return null;
			String[] ret=new String[otherZTVs.size()];
			for (int i=0;i < otherZTVs.size();++i)
				ret[i] = otherZTVs.get(i);
			return ret;
		}
		catch (Exception e)
		{}
		return null;
	}

	private boolean isMethodBelongToOtherClass(MCPESymbol symbol)
	{
		return !symbol.getDemangledName().startsWith(mcpeClass.getName());
	}

	private String getOwnerClass(MCPESymbol sym)throws Exception
	{
		try
		{
			String dname=sym.getDemangledName().substring(0, sym.getDemangledName().indexOf("("));
			String name=dname.substring(0, dname.lastIndexOf("::"));
			return name;
		}
		catch (Exception e)
		{
			throw new Exception("No owner class found.");
		}
	}

	public String[] generate()
	{
		Vector<String> lines=new Vector<String>();
		try
		{
			lines.addElement("#pragma once");
			lines.addElement("");
			lines.addElement("//This header template file is generated by MCPE-Dumper.");
			lines.addElement("");

			for (String space:namespace)
			{
				lines.addElement("namespace " + space);
				lines.addElement("{");
			}

			lines.addElement("class " + className);
			String[] extendedClasses=getExtendedClasses();
			if (extendedClasses != null)
			{
				boolean isFirstExtendClass=true;
				for (String str:extendedClasses)
				{
					if (isFirstExtendClass)
					{
						lines.addElement(" : public " + str);
						isFirstExtendClass = true;
					}
					else
						lines.addElement(" , public " + str);
				}
			}

			lines.addElement("{");
			lines.addElement("public:");
			lines.addElement("	//Fields");
			lines.addElement("	char filler_" + className + "[UNKNOW_SIZE];");

			if (getVtables() != null)
			{
				lines.addElement("public:");
				lines.addElement("	//Virtual Tables");

				for (MCPESymbol symbol:getVtables())
				{
					try
					{
						String mname=getVirtualMethodDefinition(symbol);
						lines.addElement("	" + mname);
					}
					catch (Exception e)
					{}
				}
			}

			if (getMethods() != null)
			{
				lines.addElement("public:");
				lines.addElement("	//Methods");

				for (MCPESymbol symbol:getMethods())
				{
					String mname=getMethodDefinition(symbol);
					lines.addElement("	" + mname);
				}
			}

			if (getObjects() != null)
			{
				lines.addElement("public:");
				lines.addElement("	//Objects");
				for (MCPESymbol symbol:getObjects())
				{
					String mname=getObjectDefinition(symbol);
					lines.addElement("	" + mname);
				}
			}
			lines.addElement("};//" + className);
			for (String space:namespace)
			{
				lines.addElement("}//" + space);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		String[] ret=new String[lines.size()];
		for (int i=0;i < lines.size();++i)
			ret[i] = lines.get(i);
		return ret;
	}

	private String getObjectDefinition(MCPESymbol symbol)
	{
		String name=symbol.getDemangledName().substring(mcpeClass.getName().length() + 2, symbol.getDemangledName().length());
		return "static " + mcpeClass.getName() + " * " + name + ";	//" + symbol.getName();
	}

	private String getMethodDefinition(MCPESymbol symbol)
	{
		String name=symbol.getDemangledName().substring(mcpeClass.getName().length() + 2, symbol.getDemangledName().length());
		if (name.startsWith("~" + className))
			return name + ";	//" + symbol.getName();
		if (name.startsWith(className))
			return name + ";	//" + symbol.getName();
		return "void " + name + ";	//" + symbol.getName();
	}

	private String getVirtualMethodDefinition(MCPESymbol symbol)throws Exception
	{
		String name_=symbol.getDemangledName();
		if (name_.equals("__cxa_pure_virtual"))
			return "//pure virtual method";
		if (!name_.startsWith(mcpeClass.getName()))
			throw new Exception("No owned vtable");
		String name=symbol.getDemangledName().substring(mcpeClass.getName().length() + 2, symbol.getDemangledName().length());
		if (name.startsWith("~" + className))
			return "virtual " + name + ";	//" + symbol.getName();
		if (name.startsWith(className))
			return "virtual " + name + ";	//" + symbol.getName();
		return "virtual void " + name + ";	//" + symbol.getName();
	}

	private MCPESymbol[] getObjects()
	{
		Vector<MCPESymbol>symbols=new Vector<MCPESymbol>();
		for (MCPESymbol symbol:mcpeClass.getSymbols())
			if (isObjectItem(symbol))
				symbols.addElement(symbol);
		MCPESymbol[] ret=new MCPESymbol[symbols.size()];
		for (int i=0;i < symbols.size();++i)
			ret[i] = symbols.get(i);
		if (symbols.isEmpty())
			return null;
		return ret;
	}

	private MCPESymbol[] getVtables()
	{
		if (vtable == null)
			return null;
		if (vtable.getVtables().isEmpty())
			return null;
		Vector<MCPESymbol>symbols=vtable.getVtables();
		for (MCPESymbol symbol:mcpeClass.getSymbols())
			if (!hasItemInList(symbols, symbol))
				symbols.addElement(symbol);
		symbols = moveConOrDesToStart(symbols);
		MCPESymbol[] ret=new MCPESymbol[symbols.size()];
		for (int i=0;i < symbols.size();++i)
			ret[i] = symbols.get(i);

		return ret;
	}

	private MCPESymbol[] getMethods()
	{
		Vector<MCPESymbol>symbols=new Vector<MCPESymbol>();
		for (MCPESymbol symbol:mcpeClass.getSymbols())
			if (isMethodItem(symbol) && !isVtable(symbol) && !hasItemInList(symbols, symbol))
				symbols.addElement(symbol);
		if (symbols.isEmpty())
			return null;
		symbols = moveConOrDesToStart(symbols);
		MCPESymbol[] ret=new MCPESymbol[symbols.size()];
		for (int i=0;i < symbols.size();++i)
			ret[i] = symbols.get(i);
		return ret;
	}

	private boolean isVtable(MCPESymbol sym)
	{
		if (vtable == null)
			return false;
		for (MCPESymbol symbol:vtable.getVtables())
			if (symbol.getDemangledName().equals(sym.getDemangledName()))
				return true;
		return false;
	}

	private static boolean hasItemInList(Vector<MCPESymbol> syms, MCPESymbol sym)
	{
		for (MCPESymbol iSym:syms)
			if (sym.getDemangledName().equals(iSym.getDemangledName()))
				return true;
		return false;
	}

	private Vector<MCPESymbol> moveConOrDesToStart(Vector<MCPESymbol> syms)
	{
		Vector<MCPESymbol> ret=new Vector<MCPESymbol>();
		for (MCPESymbol sym:syms)
			if (isCon(sym))
				ret.addElement(sym);
		for (MCPESymbol sym:syms)
			if (isDes(sym))
				ret.addElement(sym);
		for (MCPESymbol sym:syms)
			if (!isDes(sym) && !isCon(sym))
				ret.addElement(sym);
		return ret;
	}

	private boolean isCon(MCPESymbol symbol)
	{
		try
		{
			String name=symbol.getDemangledName().substring(mcpeClass.getName().length() + 2, symbol.getDemangledName().length());
			if (name.startsWith(className))
				return true;
		}
		catch (Exception e)
		{}
		return false;
	}

	private boolean isDes(MCPESymbol symbol)
	{
		try
		{
			String name=symbol.getDemangledName().substring(mcpeClass.getName().length() + 2, symbol.getDemangledName().length());
			if (name.startsWith("~" + className))
				return true;
		}
		catch (Exception e)
		{}
		return false;
	}

	private static boolean isObjectItem(MCPESymbol sym)
	{
		String dname=sym.getDemangledName();
		String name=sym.getName();
		if (dname.indexOf("(") == -1 && dname.indexOf(")") == -1 && name.startsWith("_ZN"))
			return true;
		return false;
	}

	private static boolean isMethodItem(MCPESymbol sym)
	{
		String dname=sym.getDemangledName();
		String name=sym.getName();
		if (dname.indexOf("(") != -1 && dname.indexOf(")") != -1 && name.startsWith("_ZN"))
			return true;
		return false;
	}
}
