/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.api;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Iterator;

import org.neo4j.kernel.api.schema.NodePropertyDescriptor;
import org.neo4j.kernel.api.exceptions.schema.AlreadyConstrainedException;
import org.neo4j.kernel.api.exceptions.schema.AlreadyIndexedException;
import org.neo4j.kernel.api.exceptions.schema.DropIndexFailureException;
import org.neo4j.kernel.api.exceptions.schema.IllegalTokenNameException;
import org.neo4j.kernel.api.exceptions.schema.IndexBelongsToConstraintException;
import org.neo4j.kernel.api.exceptions.schema.NoSuchIndexException;
import org.neo4j.kernel.api.exceptions.schema.SchemaKernelException;
import org.neo4j.kernel.api.schema.IndexDescriptor;
import org.neo4j.kernel.api.schema.IndexDescriptorFactory;
import org.neo4j.kernel.impl.api.operations.KeyWriteOperations;
import org.neo4j.kernel.impl.api.operations.SchemaReadOperations;
import org.neo4j.kernel.impl.api.operations.SchemaWriteOperations;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.neo4j.helpers.collection.Iterators.iterator;

public class DataIntegrityValidatingStatementOperationsTest
{
    @Rule
    public ExpectedException exception = ExpectedException.none();

    NodePropertyDescriptor descriptor = new NodePropertyDescriptor( 0, 7 );

    @Test
    public void shouldDisallowReAddingIndex() throws Exception
    {
        // GIVEN
        IndexDescriptor rule = IndexDescriptorFactory.of( descriptor );
        SchemaReadOperations innerRead = mock( SchemaReadOperations.class );
        SchemaWriteOperations innerWrite = mock( SchemaWriteOperations.class );
        DataIntegrityValidatingStatementOperations ctx =
                new DataIntegrityValidatingStatementOperations( null, innerRead, innerWrite );
        when( innerRead.indexesGetForLabel( state, rule.getLabelId() ) ).thenAnswer( withIterator( rule ) );

        // WHEN
        try
        {
            ctx.indexCreate( state, descriptor );
            fail( "Should have thrown exception." );
        }
        catch ( AlreadyIndexedException e )
        {
            // ok
        }

        // THEN
        verify( innerWrite, never() ).indexCreate( eq( state ), anyObject() );
    }

    @Test
    public void shouldDisallowAddingIndexWhenConstraintIndexExists() throws Exception
    {
        // GIVEN
        IndexDescriptor rule = IndexDescriptorFactory.of( descriptor );
        SchemaReadOperations innerRead = mock( SchemaReadOperations.class );
        SchemaWriteOperations innerWrite = mock( SchemaWriteOperations.class );
        DataIntegrityValidatingStatementOperations ctx =
                new DataIntegrityValidatingStatementOperations( null, innerRead, innerWrite );
        when( innerRead.indexesGetForLabel( state, rule.getLabelId() ) ).thenAnswer( withIterator(  ) );
        when( innerRead.uniqueIndexesGetForLabel( state, rule.getLabelId() ) ).thenAnswer( withIterator( rule ) );

        // WHEN
        try
        {
            ctx.indexCreate( state, descriptor );
            fail( "Should have thrown exception." );
        }
        catch ( AlreadyConstrainedException e )
        {
            // ok
        }

        // THEN
        verify( innerWrite, never() ).indexCreate( eq( state ), anyObject() );
    }

    @Test
    public void shouldDisallowDroppingIndexThatDoesNotExist() throws Exception
    {
        // GIVEN
        IndexDescriptor indexDescriptor = IndexDescriptorFactory.of( descriptor );
        SchemaReadOperations innerRead = mock( SchemaReadOperations.class );
        SchemaWriteOperations innerWrite = mock( SchemaWriteOperations.class );
        DataIntegrityValidatingStatementOperations ctx =
                new DataIntegrityValidatingStatementOperations( null, innerRead, innerWrite );
        when( innerRead.uniqueIndexesGetForLabel( state, indexDescriptor.getLabelId() ) ).thenAnswer( withIterator(  ) );
        when( innerRead.indexesGetForLabel( state, indexDescriptor.getLabelId() ) ).thenAnswer( withIterator( ) );

        // WHEN
        try
        {
            ctx.indexDrop( state, indexDescriptor );
            fail( "Should have thrown exception." );
        }
        catch ( DropIndexFailureException e )
        {
            assertThat(e.getCause(), instanceOf( NoSuchIndexException.class) );
        }

        // THEN
        verify( innerWrite, never() ).indexCreate( eq( state ), anyObject() );
    }

    @Test
    public void shouldDisallowDroppingIndexWhenConstraintIndexExists() throws Exception
    {
        // GIVEN
        IndexDescriptor indexDescriptor = IndexDescriptorFactory.of( descriptor );
        SchemaReadOperations innerRead = mock( SchemaReadOperations.class );
        SchemaWriteOperations innerWrite = mock( SchemaWriteOperations.class );
        DataIntegrityValidatingStatementOperations ctx =
                new DataIntegrityValidatingStatementOperations( null, innerRead, innerWrite );
        when( innerRead.uniqueIndexesGetForLabel( state, indexDescriptor.getLabelId() ) ).thenAnswer(
                withIterator( indexDescriptor ) );
        when( innerRead.indexesGetForLabel( state, indexDescriptor.getLabelId() ) ).thenAnswer( withIterator() );

        // WHEN
        try
        {
            ctx.indexDrop( state, indexDescriptor );
            fail( "Should have thrown exception." );
        }
        catch ( DropIndexFailureException e )
        {
            assertThat(e.getCause(), instanceOf( IndexBelongsToConstraintException.class) );
        }

        // THEN
        verify( innerWrite, never() ).indexCreate( eq( state ), anyObject() );
    }

    @Test
    public void shouldDisallowDroppingConstraintIndexThatDoesNotExists() throws Exception
    {
        // GIVEN
        IndexDescriptor indexDescriptor = IndexDescriptorFactory.of( descriptor );
        SchemaReadOperations innerRead = mock( SchemaReadOperations.class );
        SchemaWriteOperations innerWrite = mock( SchemaWriteOperations.class );
        DataIntegrityValidatingStatementOperations ctx =
                new DataIntegrityValidatingStatementOperations( null, innerRead, innerWrite );
        when( innerRead.uniqueIndexesGetForLabel( state, indexDescriptor.getLabelId() ) ).thenAnswer(
                withIterator( indexDescriptor ) );
        when( innerRead.indexesGetForLabel( state, indexDescriptor.getLabelId() ) ).thenAnswer( withIterator() );

        // WHEN
        try
        {
            ctx.indexDrop( state, indexDescriptor );
            fail( "Should have thrown exception." );
        }
        catch ( DropIndexFailureException e )
        {
            assertThat(e.getCause(), instanceOf( IndexBelongsToConstraintException.class) );
        }

        // THEN
        verify( innerWrite, never() ).indexCreate( eq( state ), anyObject() );
    }

    @Test
    public void shouldDisallowDroppingConstraintIndexThatIsReallyJustRegularIndex() throws Exception
    {
        // GIVEN
        IndexDescriptor indexDescriptor = IndexDescriptorFactory.of( descriptor );
        SchemaReadOperations innerRead = mock( SchemaReadOperations.class );
        SchemaWriteOperations innerWrite = mock( SchemaWriteOperations.class );
        DataIntegrityValidatingStatementOperations ctx =
                new DataIntegrityValidatingStatementOperations( null, innerRead, innerWrite );
        when( innerRead.uniqueIndexesGetForLabel( state, indexDescriptor.getLabelId() ) ).thenAnswer(
                withIterator( indexDescriptor ) );
        when( innerRead.indexesGetForLabel( state, indexDescriptor.getLabelId() ) ).thenAnswer( withIterator() );

        // WHEN
        try
        {
            ctx.indexDrop( state, indexDescriptor );
            fail( "Should have thrown exception." );
        }
        catch ( DropIndexFailureException e )
        {
            assertThat(e.getCause(), instanceOf( IndexBelongsToConstraintException.class) );
        }

        // THEN
        verify( innerWrite, never() ).indexCreate( eq( state ), anyObject() );
    }

    @Test
    public void shouldDisallowNullOrEmptyPropertyKey() throws Exception
    {
        KeyWriteOperations inner = mock( KeyWriteOperations.class );
        DataIntegrityValidatingStatementOperations ctx =
                new DataIntegrityValidatingStatementOperations( inner, null, null );

        try
        {
            ctx.propertyKeyGetOrCreateForName( state, null );
            fail( "Should not be able to create null property key" );
        }
        catch ( IllegalTokenNameException e )
        {   // good
        }

        try
        {
            ctx.propertyKeyGetOrCreateForName( state, "" );
            fail( "Should not be able to create empty property key" );
        }
        catch ( IllegalTokenNameException e )
        {   // good
        }
    }

    @Test
    public void shouldDisallowNullOrEmptyLabelName() throws Exception
    {
        KeyWriteOperations inner = mock( KeyWriteOperations.class );
        DataIntegrityValidatingStatementOperations ctx =
                new DataIntegrityValidatingStatementOperations( inner, null, null );

        try
        {
            ctx.labelGetOrCreateForName( state, null );
            fail( "Should not be able to create null label" );
        }
        catch ( IllegalTokenNameException e )
        {   // good
        }

        try
        {
            ctx.labelGetOrCreateForName( state, "" );
            fail( "Should not be able to create empty label" );
        }
        catch ( IllegalTokenNameException e )
        {   // good
        }
    }

    @Test( expected = SchemaKernelException.class )
    public void shouldFailInvalidLabelNames() throws Exception
    {
        // Given
        DataIntegrityValidatingStatementOperations ctx =
                new DataIntegrityValidatingStatementOperations( null, null, null );

        // When
        ctx.labelGetOrCreateForName( state, "" );
    }

    @Test( expected = SchemaKernelException.class )
    public void shouldFailOnNullLabel() throws Exception
    {
        // Given
        DataIntegrityValidatingStatementOperations ctx =
                new DataIntegrityValidatingStatementOperations( null, null, null );

        // When
        ctx.labelGetOrCreateForName( state, null );
    }

    @SafeVarargs
    private static <T> Answer<Iterator<T>> withIterator( final T... content )
    {
        return new Answer<Iterator<T>>()
        {
            @Override
            public Iterator<T> answer( InvocationOnMock invocationOnMock ) throws Throwable
            {
                return iterator( content );
            }
        };
    }

    private final KernelStatement state = StatementOperationsTestHelper.mockedState();
}
