/*

   Derby - Class org.apache.derby.impl.sql.execute.DropSPSConstantAction

   Copyright 1998, 2004 The Apache Software Foundation or its licensors, as applicable.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.impl.sql.execute;

import org.apache.derby.iapi.services.sanity.SanityManager;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;

import org.apache.derby.iapi.sql.dictionary.ColumnDescriptor;
import org.apache.derby.iapi.sql.dictionary.ConglomerateDescriptor;
import org.apache.derby.iapi.sql.dictionary.DataDescriptorGenerator;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.SPSDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;

import org.apache.derby.iapi.sql.depend.Dependency;
import org.apache.derby.iapi.sql.depend.DependencyManager;

import org.apache.derby.iapi.reference.SQLState;

import org.apache.derby.iapi.sql.execute.ConstantAction;

import org.apache.derby.iapi.sql.Activation;

import org.apache.derby.iapi.store.access.TransactionController;


/**
 *	This class  describes actions that are ALWAYS performed for a
 *	DROP STATEMENT Statement at Execution time.
 *
 *	@author Jamie
 */
class DropSPSConstantAction extends DDLConstantAction
{

	private final String				spsName;
	private final SchemaDescriptor	sd;

	// CONSTRUCTORS


	/**
	 *	Make the ConstantAction for a DROP STATEMENT statement.
	 *
	 *	@param	sd					Schema that stored prepared statement lives in.
	 *	@param	spsName				Name of the SPS
	 *
	 */
	DropSPSConstantAction(
								SchemaDescriptor	sd,
								String				spsName)
	{
		this.sd = sd;
		this.spsName = spsName;

		if (SanityManager.DEBUG)
		{
			SanityManager.ASSERT(sd != null, "SchemaDescriptor is null");
		}
	}

	///////////////////////////////////////////////
	//
	// OBJECT SHADOWS
	//
	///////////////////////////////////////////////

	public	String	toString()
	{
		// Do not put this under SanityManager.DEBUG - it is needed for
		// error reporting.
		String				schemaName = "???";

		if ( sd != null ) { schemaName = sd.getSchemaName(); }

		return "DROP STATEMENT " + schemaName + "." + spsName;
	}

	// INTERFACE METHODS


	/**
	 *	This is the guts of the Execution-time logic for DROP STATEMENT.
	 *
	 *	@see ConstantAction#executeConstantAction
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public void	executeConstantAction( Activation activation )
						throws StandardException
	{
		SPSDescriptor 				spsd;
		ConglomerateDescriptor 		cd;

		LanguageConnectionContext lcc = activation.getLanguageConnectionContext();
		DataDictionary dd = lcc.getDataDictionary();
		DependencyManager dm = dd.getDependencyManager();


		/*
		** Inform the data dictionary that we are about to write to it.
		** There are several calls to data dictionary "get" methods here
		** that might be done in "read" mode in the data dictionary, but
		** it seemed safer to do this whole operation in "write" mode.
		**
		** We tell the data dictionary we're done writing at the end of
		** the transaction.
		*/
		dd.startWriting(lcc);

		/* 
		** Get the statement descriptor.  We're responsible for raising
		** the error if it isn't found 
		*/
		spsd = dd.getSPSDescriptor(spsName, sd);

		if (spsd == null)
		{
			throw StandardException.newException(SQLState.LANG_OBJECT_NOT_FOUND_DURING_EXECUTION, "STATEMENT",
					(sd.getSchemaName() + "." + spsName));
		}

		/* Prepare all dependents to invalidate.  (This is there chance
		 * to say that they can't be invalidated.  For example, an open
		 * cursor referencing a table/sps that the user is attempting to
		 * drop.) If no one objects, then invalidate any dependent objects.
		 */
		dm.invalidateFor(spsd, DependencyManager.DROP_SPS, lcc);

		/* Clear the dependencies for the sps */
		spsd.makeInvalid(DependencyManager.DROP_SPS, lcc);
		//dm.clearDependencies(spsd);

		/* Drop the sps */
		dd.dropSPSDescriptor(spsd, lcc.getTransactionExecute());
	}
}
